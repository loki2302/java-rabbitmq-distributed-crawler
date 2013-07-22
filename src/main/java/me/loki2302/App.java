package me.loki2302;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


import org.codehaus.jackson.map.ObjectMapper;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;

public class App {    
    public static void main(String[] args) throws InterruptedException, IOException {        
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(Config.RabbitHostName);
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();
        CrawlerProtocol.initialize(channel);
        CrawlerProtocol.reset(channel);        
        channel.close();
        connection.close();
        
        Thread managerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                ManagementService managementService = makeManagementService(Config.RabbitHostName);                                
                ManagerApp managerApp = new ManagerApp(managementService);
                managerApp.run();                
            }            
        });
        
        List<Thread> workerThreads = new ArrayList<Thread>();
        for(int i = 0; i < 80; ++i) {
            Thread workerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    WorkerService workerService = makeWorkerService(Config.RabbitHostName);                    
                    WorkerApp workerApp = new WorkerApp(workerService);
                    workerApp.run();                
                }            
            });
            workerThreads.add(workerThread);
        }        
        
        managerThread.start();
        for(Thread workerThread : workerThreads) {
            workerThread.start();
        }
        
        managerThread.join();
        for(Thread workerThread : workerThreads) {
            workerThread.join();
        }
    }
    
    private static ManagementService makeManagementService(String rabbitHostName) {
        try {
            Channel channel = makeChannel(rabbitHostName);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonSerializer jsonSerializer = new JsonSerializer(objectMapper);
            QueueingConsumer resultConsumer = new QueueingConsumer(channel);
            channel.basicConsume(CrawlerProtocol.RESULT_QUEUE_NAME, true, resultConsumer);
            QueueingConsumer taskProgressConsumer = new QueueingConsumer(channel);
            channel.basicConsume(CrawlerProtocol.TASK_PROGRESS_QUEUE_NAME, true, taskProgressConsumer);
            ManagementService managementService = new ManagementService(
                    jsonSerializer, 
                    channel, 
                    resultConsumer, 
                    taskProgressConsumer);
            
            return managementService;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static WorkerService makeWorkerService(String rabbitHostName) {
        try {
            Channel channel = makeChannel(rabbitHostName);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonSerializer jsonSerializer = new JsonSerializer(objectMapper);
            QueueingConsumer taskConsumer = new QueueingConsumer(channel);
            channel.basicConsume(CrawlerProtocol.TASK_QUEUE_NAME, true, taskConsumer);
            WorkerService workerService = new WorkerService(jsonSerializer, channel, taskConsumer);
            return workerService;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
        
    private static Channel makeChannel(String rabbitHostName) {
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(rabbitHostName);
            Connection connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel();                       
            CrawlerProtocol.initialize(channel);
            return channel;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
