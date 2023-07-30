package org.example;

import software.amazon.awssdk.services.sqs.model.Message;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.UsefulFunctions.*;


public class LocalApp {
    public static boolean workerCounterThread = true;
    public static boolean workersAtomicCounterInit = false;
    public static AtomicInteger atomicCounter = new AtomicInteger(-1);
    public static int numOfWorkersPerMsg ;
    public static String localApp_managerURL;
    public static String manager_workersURL;
    public static String manager_localAppURL;
    public static String workers_managerURL;
    public static String managerInstanceID;
    public static boolean runningManagerThread=true;

    public static void main(String[] args) throws IOException {
        String inputFileName = args[0];
        String outputFileName = args[1];
        numOfWorkersPerMsg = Integer.parseInt(args[2]);
        String terminate = args[3];

//        String inputFileName = "input-example.txt";
//        String outputFileName = "output.html";
//        numOfWorkersPerMsg = 5;
//        String terminate = "terminate";


        boolean running = true;

        File inputFile = new File(inputFileName);

        //initialize EC2, S3, SQS
        initializeServices();

        if (!isManagerRunning()) {
            //create Manager instance
            managerInstanceID = createEC2Instance(ec2, "Manager", "ami-0b0dcb5067f052a63");
        } else
            System.out.println("manager is already running");


        //create SQS queues
        localApp_managerURL = createQueue(sqsClient, queueName3);


        String id = String.valueOf(System.currentTimeMillis());
        keyName1 = keyName1 + id;
        queuesURLs.put("managerToLocal"+id,createQueue(sqsClient,"managerToLocal"+id));

        //upload file to S3
        uploadFileToS3(bucketName, keyName1, inputFile);

        //send message to manager with file location in S3
        sendMessage("newTask" + "," + bucketName + "," + keyName1 + "," + id+","+numOfWorkersPerMsg, localApp_managerURL);


        if (terminate.equals("terminate")) {
            //send terminate message to manager
            sendMessage("terminate", localApp_managerURL);
        }

        while (running) {

            //get message if process is done
            List<Message> msg = getMessagesFromSQS(queuesURLs.get("managerToLocal"+id));
            if (msg.isEmpty()) {
                continue;
            }
            String[] splitMsg = msg.get(0).body().split(",", 5);

            //delete received message
            deleteMessage(queuesURLs.get("managerToLocal"+id), msg.get(0));

            if (splitMsg[0].equals("processIsDone")) {
                System.out.println("recived: processIsDone");
                String bucket = splitMsg[1];
                String key = splitMsg[2];

                //get the output file from S3
                File f = downloadAndCreateHTML(bucket, key, outputFileName);
                System.out.println("html file created");
                break;
            }
        }
    }

}
