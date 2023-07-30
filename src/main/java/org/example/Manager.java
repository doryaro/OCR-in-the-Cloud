package org.example;

import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.LocalApp.*;
import static org.example.Manager.currWorkerCounter;
import static org.example.Manager.workersCounter;
import static org.example.UsefulFunctions.*;


class Worker_to_Manger_Thread extends Thread {
    public String filename;

    public Worker_to_Manger_Thread(String fileName) {
        this.filename = fileName;
    }

    public void run() {
        try {
            // Displaying the thread that is running
            System.out.println("Worker_to_Manger_Thread " + Thread.currentThread().getId() + " is running");
            FileWriter myWriter;


            while (true) {

                //check if there is message
                List<Message> msg = getMessagesFromSQS(workers_managerURL);
                if (msg.isEmpty())
                    continue;

                //get message data
                String msgText = msg.get(0).body();
                String[] splitMsg = msgText.split(",", 5);
                System.out.println("manager recived message: " + msgText);


                if (splitMsg[0].equals("doneOCR")) {
                    //insert url and extracted text to output.txt file
                    String url = splitMsg[1];
                    String extractedText = splitMsg[2];

                    extractedText = extractedText.replace('\n', ' ');
                    //delete received message
                    deleteMessage(workers_managerURL, msg.get(0));
                    atomicCounter.addAndGet(-1);
                    System.out.println("atomic message counter is: " + atomicCounter.get());
                    System.out.println(url);
                    //insert url and extracted text to output file
                    myWriter = new FileWriter(filename, true);
                    myWriter.write(url + "," + extractedText + "\n");
                    myWriter.close();


                }

            }
        } catch (Exception e) {
            // Throwing an exception
            System.out.println("Worker_to_Manger_Thread -> Exception is caught");
            e.printStackTrace();
        }
    }
}

class managerThread extends Thread {
    public String[] msg;

    public managerThread(String[] msg) {
        this.msg = msg.clone();
    }

    List<String> workersIDs = new ArrayList<>();

    public void run() {
        String id = msg[3];
        numOfWorkersPerMsg = Integer.parseInt(msg[4]);
        queuesURLs.put("managerToLocal" + id, createQueue(sqsClient, "managerToLocal" + id));
        manager_workersURL = createQueue(sqsClient, queueName2);
        workers_managerURL = createQueue(sqsClient, queueName4);

        String fileName = "output" + id + ".txt";
        Worker_to_Manger_Thread workersTomanager = new Worker_to_Manger_Thread(fileName);
        workersTomanager.start();

        try {
            File outputFile = new File(fileName);

            // Displaying the thread that is running
            System.out.println("Local_to_Manger_Thread " + Thread.currentThread().getId() + " is running");
            String instanceID;


            //download URLs file into a List of String urls
            List<String> URLs = downloadFileFromS3(msg[1], msg[2]);

            //send each url as a message to the workers
            for (String url : URLs) {
                sendMessage(url + "," + id, manager_workersURL);
                if (!workersAtomicCounterInit) {
                    atomicCounter = new AtomicInteger(0);
                    workersAtomicCounterInit = true;
                }
                atomicCounter.addAndGet(1);
            }
            System.out.println("url list size is: " + URLs.size());

            //create workers
            synchronized (this) {
                int numOfWorkersToOpen = URLs.size() / numOfWorkersPerMsg;
                if (URLs.size() % numOfWorkersPerMsg != 0)
                    numOfWorkersToOpen++;
                if (numOfWorkersToOpen > workersCounter.get()) {
                    numOfWorkersToOpen = numOfWorkersToOpen - workersCounter.get();
                    if (workersCounter.get() < 10) {
                        for (int i = 0; i < numOfWorkersToOpen; i++) {
                            instanceID = createEC2Instance(ec2, "Worker" + workersCounter, "ami-0b0dcb5067f052a63");
                            workersIDs.add(instanceID);
                            workersCounter.addAndGet(1);
                        }
                    }
                }
            }


            while (runningManagerThread) {

                //check if there is message
                List<Message> msg = getMessagesFromSQS(localApp_managerURL);
                if (msg.isEmpty()) {
                    continue;
                }


                //get message data
                String msgText = msg.get(0).body();
                String[] splitMsg = msgText.split(",", 5);
                System.out.println("manager recived message: " + msgText);


                if (splitMsg[0].equals("terminate") && newTaskRecived) {

                    //delete received message
                    deleteMessage(localApp_managerURL, msg.get(0));
                    runningManagerThread = false;
                }
            }

            // wait for all workers to finish their tasks
            while (atomicCounter.get() != 0) {
                System.out.println("waiting for threads to finish,id: " + id);
                sleep(1000);
            }
            workerCounterThread = false;
            sleep(5000);
            // terminate running workers
            for (String workerid : workersIDs)
                terminateInstance(workerid);

            String keyName3 = keyName2 + id;
            // upload output file to S3
            uploadFileToS3(bucketName, keyName3, outputFile);
            sendMessage("processIsDone," + bucketName + "," + keyName3 + "," + id, queuesURLs.get("managerToLocal" + id));

            System.out.println("manager is terminating");
            Runtime.getRuntime().exec(new String[]{"shutdown", "now"});


        } catch (Exception e) {
            // Throwing an exception
            System.out.println("Local_to_Manger_Thread -> Exception is caught");
            e.printStackTrace();
        }
    }
}

class WorkerCounterThread extends Thread {
    public void run() {
        System.out.println("worker counter thread " + Thread.currentThread().getId() + " is running");

        try {
            sleep(60000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        while (workerCounterThread) {
            try {
                sleep(2000);
            } catch (InterruptedException e) {
                continue;
            }
            String nextToken = null;
            int count = 0;
            try {

                do {
                    DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(20).nextToken(nextToken).build();
                    DescribeInstancesResponse response = ec2.describeInstances(request);

                    for (Reservation reservation : response.reservations()) {
                        for (Instance instance : reservation.instances()) {
                            if (!instance.tags().isEmpty() && (instance.tags().get(0).value().contains("Worker")) &&
                                    ((instance.state().name().toString().equals("running")) ||
                                            (instance.state().name().toString().equals("pending"))))
                                count++;
                        }
                    }
                    nextToken = response.nextToken();
                } while (nextToken != null);

            } catch (Ec2Exception e) {
                System.err.println(e.awsErrorDetails().errorMessage() + "WorkerCount()");
                System.exit(1);
            }
            currWorkerCounter = count;
            System.out.println("count: " + count);
            if (currWorkerCounter < workersCounter.get()) {
                System.out.println("workercounteratomic: " + workersCounter.get());
                int n = workersCounter.get() - currWorkerCounter;
                for (int i = 0; i < n; i++) {
                    createEC2Instance(ec2, "Worker" + i + " backup", "ami-0b0dcb5067f052a63");
                }
            }
        }
    }
}

public class Manager {
    public static AtomicInteger workersCounter = new AtomicInteger(0);
    public static int currWorkerCounter = 0;


    public static void main(String[] args) throws IOException {

        //initialize EC2, S3, SQS
        initializeServicesNoCredintials();
        localApp_managerURL = createQueue(sqsClient, queueName3);


        WorkerCounterThread counterThread = new WorkerCounterThread();
        counterThread.start();

        while (true) {

            //check if there is message
            List<Message> msg = getMessagesFromSQS(localApp_managerURL);
            if (msg.isEmpty())
                continue;

            //get message data
            String msgText = msg.get(0).body();
            String[] splitMsg = msgText.split(",", 5);
            System.out.println("manager received message: " + msgText);

            if (splitMsg[0].equals("newTask")) {
                newTaskRecived = true;

                //delete received message
                deleteMessage(localApp_managerURL, msg.get(0));

                managerThread LocalappToManager = new managerThread(splitMsg);
                LocalappToManager.start();
            }


        }


    }

}



