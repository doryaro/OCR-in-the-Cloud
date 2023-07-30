package org.example;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import static org.example.LocalApp.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UsefulFunctions {
    public static Ec2Client ec2;
    public static S3Client s3;
    public static SqsClient sqsClient;
    public static String bucketName = "mybucket23sdfr";
    public static String keyName1 = "key239_kjdfhh";
    public static String keyName2 = "key2394df*4th";
    public static String queueName1 = "manager-local-13a3d";
    public static String queueName2 = "manager-workers-57de4";
    public static String queueName3 = "local-manager-87ju7";
    public static String queueName4 = "workers-manager-66h7g";
    public static boolean newTaskRecived = false;
    public static Map<String,String> queuesURLs =new ConcurrentHashMap<>();



    public static void initializeServices() {
        software.amazon.awssdk.regions.Region region = software.amazon.awssdk.regions.Region.US_EAST_1;
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();

        // initialize EC2
        ec2 = Ec2Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();

        // initialize S3
        s3 = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        createBucket(s3, bucketName);

        // initialize SQS
        sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();
    }

    public static void initializeServicesNoCredintials() {

        software.amazon.awssdk.regions.Region region = software.amazon.awssdk.regions.Region.US_EAST_1;

        // initialize EC2
        ec2 = Ec2Client.builder()
                .region(region)
                .build();

        // initialize S3
        s3 = S3Client.builder()
                .region(region)
                .build();
        createBucket(s3, bucketName);

        // initialize SQS
        sqsClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .build();
    }


    public static String createEC2Instance(Ec2Client ec2, String name, String amiId) {
        String userData = "";
        if (name.equals("Manager")) {
            userData = "#!/bin/bash" + "\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK1 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "sudo yum update -y\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK2 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "sudo yum install java-devel -y" + "\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK3 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "java -version\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK4 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "aws s3 cp s3://input-jar-file7826aj34/ass1Manager.jar ." + " >nul 2>&1\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK5 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "java -jar ass1Manager.jar\n";
        }
        if (name.contains("Worker")) {
            userData = "#!/bin/bash" + "\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK1 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "sudo yum update -y\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK2 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "sudo yum install java-devel -y" + "\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK3 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "java -version\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK4 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "aws s3 cp s3://input-jar-file7826aj34/ass1Worker.jar ." + " >nul 2>&1\n";
            userData = userData + "echo !!!!!!!!!!!!!!!!!!!!! CHECK5 !!!!!!!!!!!!!!!!!!!!!!!!!!\n";
            userData = userData + "java -jar ass1Worker.jar\n";
        }


        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.T2_MICRO)
                .maxCount(1)
                .minCount(1)
                .instanceInitiatedShutdownBehavior("terminate")
                .userData(Base64.getEncoder().encodeToString(userData.getBytes()))
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                .build();


        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceId = response.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf(
                    "Successfully started EC2 Instance %s based on AMI %s \n",
                    instanceId, amiId);

            return instanceId;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }

    // Create a bucket by using a S3Waiter object
    public static void createBucket(S3Client s3Client, String bucketName) {

        try {
            S3Waiter s3Waiter = s3Client.waiter();
            CreateBucketRequest bucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3Client.createBucket(bucketRequest);
            HeadBucketRequest bucketRequestWait = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            // Wait until the bucket is created and print out the response.
            WaiterResponse<HeadBucketResponse> waiterResponse = s3Waiter.waitUntilBucketExists(bucketRequestWait);
            waiterResponse.matched().response().ifPresent(System.out::println);
            System.out.println(bucketName + " is ready");

        } catch (S3Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static String createQueue(SqsClient sqsClient, String queueName) {

        try {


            CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build();

            sqsClient.createQueue(createQueueRequest);


            GetQueueUrlResponse getQueueUrlResponse = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build());
            System.out.println("Queue created");
            return getQueueUrlResponse.queueUrl();

        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return "";
    }

    public static void sendMessage(String msgBody, String queueURL) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueURL)
                .messageBody(msgBody)
                .build());
    }

    public static void uploadFileToS3(String bucketName, String keyName, File file) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();
        s3.putObject(objectRequest, RequestBody.fromFile(file));
    }

    public static List<String> getInstanceIdList(Ec2Client ec2) {

        String nextToken = null;
        List<String> instanceIdList = new ArrayList<>();
        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        instanceIdList.add(instance.instanceId());


                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return instanceIdList;
    }


    public static void describeEC2Instances(Ec2Client ec2) {

        boolean done = false;
        String nextToken = null;

        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        System.out.println("Instance Id is " + instance.instanceId());
                        System.out.println("Image id is " + instance.imageId());
                        System.out.println("Instance type is " + instance.instanceType());
                        System.out.println("Instance state name is " + instance.state().name());
                        System.out.println("monitoring information is " + instance.monitoring().state());

                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public static List<Message> getMessagesFromSQS(String queueURL) {
        try {
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueURL)
                    .maxNumberOfMessages(1)
                    .build();
            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
            return messages;
        } catch (SqsException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return null;
    }

    public static List<String> downloadFileFromS3(String bucketName, String keyName) throws IOException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();
        ResponseInputStream<GetObjectResponse> inputStream = s3.getObject(getObjectRequest);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        List<String> URLs = new ArrayList<>();
        String url = "";
        while (url != null) {
            url = reader.readLine();
            if (url != null)
                URLs.add(url);

        }
        return URLs;

    }

    public static File downloadAndCreateHTML(String bucketName, String keyName, String filename) throws IOException {
        File htmlFile = new File(filename);
        BufferedWriter buff = new BufferedWriter(new FileWriter(htmlFile));
        buff.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\"><title>OCR</title>\n" +
                "</head><body>");
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();
        ResponseInputStream<GetObjectResponse> inputStream = s3.getObject(getObjectRequest);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = "";
        while (line != null) {
            line = reader.readLine();
            System.out.println(line);
            if (line != null) {
                String[] words = line.split(",", 2);
                buff.write("<p>\n" + "\t\t<img src=" + words[0] + "><br>\n" +
                        "\t\t" + words[1] +
                        "\t</p>");
            }
        }
        buff.write("\n</body></html>");
        buff.close();
        return htmlFile;
    }

    public static boolean isManagerRunning() {
        String nextToken = null;
        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(20).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        if (!instance.tags().isEmpty()&&(instance.tags().get(0).value().equals("Manager")) && (instance.state().name().toString().equals("running")))
                            return true;
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return false;
    }


    public static boolean isWorkersFinished() {
        String nextToken = null;
        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(20).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        if ((instance.tags().get(0).value().contains("Worker")) && !(instance.state().name().toString().equals("terminate")))
                            return false;
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return true;
    }


    public static void deleteMessage(String queueURL, Message msg) {
        try {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueURL)
                    .receiptHandle(msg.receiptHandle())
                    .build();
            sqsClient.deleteMessage(deleteMessageRequest);

        } catch (Exception e) {
            System.out.println(e.getStackTrace());
        }
    }

    public static void terminateInstance(String instanceId) {
        TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();
        ec2.terminateInstances(request);
        System.out.printf("Successfully terminated instance %s \n", instanceId);
    }

    public static int WorkersCountFunction() {
        String nextToken = null;
        int count = 0;
        try {

            do {
                DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(20).nextToken(nextToken).build();
                DescribeInstancesResponse response = ec2.describeInstances(request);

                for (Reservation reservation : response.reservations()) {
                    for (Instance instance : reservation.instances()) {
                        if ((instance.tags().get(0).value().contains("Worker")) && (instance.state().name().toString().equals("running")))
                            count++;
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage() + "WorkerCount()");
            System.exit(1);
        }
        return count;
    }


}
