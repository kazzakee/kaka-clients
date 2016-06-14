package com.kz.kafka10.consumer;
 
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
public class ConsumerGroup {
	private static final Logger log = LoggerFactory.getLogger(ConsumerGroup.class);

    private KafkaConsumer<String, String> consumer;
    private ScheduledExecutorService executor = null;
	private boolean keepRunning = true;
 
    public ConsumerGroup(String bootstrapServers, String groupId, String[] topics) {
        consumer = new KafkaConsumer<String, String>(createConsumerConfig(bootstrapServers, groupId));
        consumer.subscribe(Arrays.asList(topics));
        executor = Executors.newScheduledThreadPool(2 * topics.length);
    }
 
	public void shutdown() {
		log.info("Shutdown() started");
		this.keepRunning = false;
		if (executor != null)
			executor.shutdown();
		try {
			if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
				log.info("Timed out waiting for consumer threads to shut down, exiting uncleanly");
			}
		} catch (InterruptedException e) {
			log.error("Interrupted during shutdown, exiting uncleanly");
		}
		log.info("total records consumed ="+ProcessorThread.getConsumedCount());
		log.info("Shutdown() completed");
	}
 
    public void start() throws Exception {
    	log.info("Starting consumer thread");
    	Thread thread = new Thread() {
    		public void run() {
    			// now create an loop to poll and consume the messages
    	    	log.info("Start poll() for messages");
    	        while (keepRunning) {
    	            ConsumerRecords<String, String> records = consumer.poll(200);
    	            if(records.count() > 0) {
    		            ProcessorThread worker = new ProcessorThread(records);
    		            executor.execute(worker);
    		            log.info("total records consumed ="+ProcessorThread.getConsumedCount());
    	            }
    	        }
    			if (consumer != null)
    				consumer.close();
    		};
    	};
    	thread.start();
    }
     
    private static Properties createConsumerConfig(String bootstrapServers, String groupId) {
        Properties props = new Properties();
        props.put("bootstrap.servers",bootstrapServers);
        props.put("group.id", groupId);
        props.put("enable.auto.commit","true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("max.partition.fetch.bytes","2097152");//2mb
        props.put("key.deserializer",org.apache.kafka.common.serialization.StringDeserializer.class.getCanonicalName());
        props.put("value.deserializer",org.apache.kafka.common.serialization.StringDeserializer.class.getCanonicalName());
        
        return props;
    }
 
    public static void main(String[] args) {
        String bootstrapServers = args[0];
        String groupId = args[1];
        String[] topics = args[2].split(",");
 
        try {
        	ConsumerGroup example = new ConsumerGroup(bootstrapServers, groupId, topics);
			example.start();
			// wait for 30 seconds before shutting down
			try {
			    Thread.sleep(30000);
			} catch (InterruptedException ie) {
				log.error("Interrupted in sleep...");
			}
			example.shutdown();
		} catch (Exception e) {
			log.error("Exception encountered", e);
		}
    }
}