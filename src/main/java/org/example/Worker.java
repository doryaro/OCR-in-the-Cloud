package org.example;

import com.asprise.ocr.Ocr;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.imageio.ImageIO;
import javax.sound.midi.Soundbank;

import static org.example.LocalApp.*;
import static org.example.Manager.*;
import static org.example.UsefulFunctions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;


public class Worker {


    public static void main(String[] args) throws IOException {

        initializeServicesNoCredintials();
        manager_workersURL = createQueue(sqsClient, queueName2);
        workers_managerURL = createQueue(sqsClient, queueName4);
        boolean running = true;
        while (running) {
            boolean found =false;

            //get messages from SQS
            List<Message> msg = getMessagesFromSQS(manager_workersURL);
            if (!msg.isEmpty()) {
                //get message data
                String msgText = msg.get(0).body();
                String[] splitMsg = msgText.split(",", 5);
                String url=splitMsg[0];
                String id =splitMsg[1];

                System.out.println("message received: " +url+" , "+id);

                //OCR
                String extractedText;
                URL[] arr = new URL[1];
                System.out.println(url);
                arr[0] = new URL(url);
                Ocr.setUp(); // one time setup
                Ocr ocr = new Ocr(); // create a new OCR engine
                ocr.startEngine("eng", Ocr.SPEED_FASTEST); // English
                try {
                    extractedText = ocr.recognize(arr, Ocr.RECOGNIZE_TYPE_ALL, Ocr.OUTPUT_FORMAT_PLAINTEXT);
                    if(extractedText.contains("error"))
                        extractedText ="OCR operation FAILED!!!";
                } catch (Exception e) {
                    extractedText = "OCR operation FAILED!!!";
                }

                // notify manager that ocr is done via doneOCR message (sent url and identified text)
                sendMessage("doneOCR" + "," + url + "," + extractedText+","+id, workers_managerURL);

                //delete received message
                deleteMessage(manager_workersURL, msg.get(0));

            }
        }


    }


}
