/* Aarti Nimhan (801098198)
 * 
 * This class creates one router and assigns it a unique port number.
 * Each router will have 2 threads one which listens to requests from other routers. This thread will accept the vectors from neighboring routers and update its own routing table.
 * The second thread will send its own updated vectors to all its neighbors. 
 * This class also implements Poisoned Reverse mechanism to handle looping of bad news. 
 * */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
/**
 * @author Aarti
 *
 */

public class CreateRouter {
// Enter Port Number of router and Path of file in args	
	public static File portNumberFile;
	public static HashMap<String,Integer>idPortNumbersMap;
	public static HashMap<String,Integer>idRowIndexMap;
	public static Double[][] routingTable;
	public static ArrayList<String> neighborList;
	public static HashMap<String,Double> costToEachRouterMap;
	public static HashMap<String,String> hopRouteMap;
	public static String routingFilePath;
	public static File routingFile;
	public static String myId;
	public static int totalNumberOfRouters;
	public static DatagramSocket routerSocket;
	public static int outputCounter =1;
	public static final Double max = Double.MAX_VALUE;
	public static DatagramPacket sendPacket;
	public static DatagramPacket receivedPacket;
	
	public static void main(String[] args) {

		//Validating commandline arguements
		if(args.length<2) {
			System.out.println("Invalid Arguments! Port number File and File Path expected.");
			System.exit(0);
		}else {
			//Reading portNumbers from file
			String path = args[0];
			portNumberFile = new File(path);
			idRowIndexMap = new HashMap<String, Integer>();
			idPortNumbersMap = new HashMap<String, Integer>();
			System.out.println("portNmberFile "+ portNumberFile);
			//Validating PortNumber File
			if (!portNumberFile.exists()) {
				System.out.println("File does not exist.");
				System.exit(0);
			}else {
				try {
					BufferedReader bufferedReaderObj = new BufferedReader(new FileReader(portNumberFile));
					String line = bufferedReaderObj.readLine();
					int count=0;
					while(line != null && !line.trim().equals("")) {
						String[] temp = line.split(" ");
						//Validating and assigning port numbers to routers.
						if(Integer.parseInt(temp[1])<= 1024 || Integer.parseInt(temp[1])>=65535) {
							System.out.println("Invalid Port Number. Port Number should be in range 1024 to 65535");
							throw new NumberFormatException();
						}else {
							idRowIndexMap.put(temp[0], count);
							count++;
							idPortNumbersMap.put(temp[0], Integer.parseInt(temp[1]));
						}
						line = bufferedReaderObj.readLine();
					}
					bufferedReaderObj.close();
					totalNumberOfRouters = count;
					routingTable = new Double[totalNumberOfRouters][totalNumberOfRouters];
				}catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			// Reading router file.
			validatingAndSettingRoutingFilePath(args[1]);
			costToEachRouterMap = new HashMap<String, Double>();
			hopRouteMap = new HashMap<String, String>();
			//Initializing costs to each router and Hop required for that route
			for(int i=0;i<totalNumberOfRouters;i++) {
				costToEachRouterMap.put(getKey(idRowIndexMap, i), max);
				hopRouteMap.put(getKey(idRowIndexMap, i), "-");
			}
			//Setting cost to self as 0.0
			costToEachRouterMap.put(myId, 0.0);
			neighborList = new ArrayList<String>();
			try {
				routerSocket = new DatagramSocket(idPortNumbersMap.get(myId));
			} catch (SocketException e1) {
				System.out.println("Socket Exception for router with id : "+ myId);
				e1.printStackTrace();
			}
			//Initializing routing table with all values as max and values to self as 0 for all routers.
			for(int i=0; i<totalNumberOfRouters;i++) {
				Arrays.fill(routingTable[i],max);
				routingTable[i][i]=0.0;
			}
			
			//Creating sender thread to send vector packets to neighbors
			Runnable runnableReadFileChanges = new SenderThread(); 
			Thread senderThread = new Thread(runnableReadFileChanges);
			senderThread.start();

			//Creating receiver thread to receive vector packets from neighbors
			while(true) {
				byte[] content = new byte[1024];
				int size = content.length;
				try {	
					//Receiving vector packets from neighbors
					receivedPacket = new DatagramPacket(content, size);
					routerSocket.receive(receivedPacket);
					int length = receivedPacket.getLength();
					String temp = new String(receivedPacket.getData(), 0, length);
					String receiverId =  getKey(idPortNumbersMap, receivedPacket.getPort());
					int receiverIndex = idRowIndexMap.get(receiverId);
					String[] receivedVector =temp.split(":");
					synchronized (routingTable) {
						//Updating received vector in routing table in a synchronization.
						for(int i =0 ;i<totalNumberOfRouters;i++) {
							routingTable[receiverIndex][i]= Double.parseDouble(receivedVector[i]);
						}	
					}
				} catch (IOException e) {
					System.out.println("Exception while receiving vector from neighbors..");
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * @param path - path of routing file.
	 * This method Validates routing file.
	 */
	public static void validatingAndSettingRoutingFilePath(String path) {
		routingFilePath = path;
		routingFile = new File(routingFilePath);  
		if (!routingFile.exists()) {
			System.out.println("File does not exist.");
			System.exit(0);
		}
		String[] temp = (routingFile.getName()).split("\\.");
		//Assigning Id to current router.
		myId = temp[0];
	}
	
	//Method to fetch Key for a particular Value
	/**
	 * @param map - Map from which key is to be extracted.
	 * @param value - Value for which key is to be extracted.
	 * @return Key for the requested value
	 * 
	 */
	public static <K,V> K getKey(Map<K,V> map, V value) {
		for(Map.Entry<K, V> entry: map.entrySet()) {
			if(value.equals(entry.getValue())) {
				return entry.getKey();
			}
		}
		return null;
	}
	
	/**
	 * This method Reads the neighbors and the cost to reach those neighbors from the routing file and updates the routing table accordingly
	 */
	public static void updateRoutingTableFromFile() {
		//Clearing self vector in routing table
		int myIndex =  idRowIndexMap.get(myId);
		for(int i =0; i < totalNumberOfRouters; i++)
			routingTable[myIndex][i] = max;
		routingTable[myIndex][myIndex] = 0.0;

		BufferedReader bufferedReaderObj;
		try {
			bufferedReaderObj = new BufferedReader(new FileReader(routingFile));
			int numberOflines = Integer.parseInt(bufferedReaderObj.readLine());
			//Adding neighbors mentioned in the routing file to a List 
			for( int i=0; i< numberOflines; i++) {
				String[] neighborDetails = bufferedReaderObj.readLine().split(" ");
				neighborList.add(neighborDetails[0]); 
				//Finding neighbor Index to insert cost at correct cell of routing table.
				routingTable[myIndex][idRowIndexMap.get(neighborDetails[0])]=Double.parseDouble(neighborDetails[1]);
				if(outputCounter == 1){
					costToEachRouterMap.put(neighborDetails[0], Double.parseDouble(neighborDetails[1]));
					hopRouteMap.put(neighborDetails[0],neighborDetails[0]);
				}
			}
			bufferedReaderObj.close();
		} catch (FileNotFoundException e) {
			System.out.println("Routing File Not Found.. ");
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
		
	/**
	 * This method prints output on the console. It print the cost to reach each destination router and the hop to the neighbor through which the minimum cost is calculated
	 */
	public static void printOutput() {
		System.out.println("> Output Number " + outputCounter +"\n");
		outputCounter++;
		int myIndex=idRowIndexMap.get(myId);
		for(int i=0; i<totalNumberOfRouters;i++) {
			//Printing routes to other than self
			if(i!=myIndex) {
				String destinationId = getKey(idRowIndexMap, i);
				//Route not available
				if(hopRouteMap.get(destinationId).equals("-") || max.equals(costToEachRouterMap.get(destinationId))) 
					System.out.println("Shortest path "+ myId + "-" + destinationId +": route not found.");
				//Route available
				else 
					System.out.println("Shortest path "+ myId + "-" + destinationId +": the next hop is " + hopRouteMap.get(destinationId) + " and the cost is " + costToEachRouterMap.get(destinationId) );
			}
		}
	
	}
	
	/**
	 * This method sends the latest vector of the router to all its neighbors using UDP connection
	 */
	public static void sendUpdateToNeighbors() {
		for(int i= 0; i< neighborList.size(); i++) {	//For each neighbor
			String neighborId = neighborList.get(i);
			String sendData = "";
			for(int j=0;j<totalNumberOfRouters;j++) {		//Computing vector in order of index of routing table
				//Finding router id mapped to index with value j
				String idForJ = getKey(idRowIndexMap, j);
				//Using Poisoned Reverse sending infinity if using neighbor for hop.
				if(neighborId.equals(hopRouteMap.get(idForJ))) 
					sendData = sendData + max + ":"; 
				//Send actual cost
				else 
					sendData = sendData + costToEachRouterMap.get(idForJ) + ":";
			}
			// Preparing packet to send 
			sendPacket = new DatagramPacket(sendData.getBytes(), sendData.getBytes().length);
			sendPacket.setAddress(InetAddress.getLoopbackAddress());
			sendPacket.setPort(idPortNumbersMap.get(neighborId));
			try {
				routerSocket.send(sendPacket);
			} catch (IOException e) {
				System.out.println("Exception when sending packet to neighbor :" + neighborId);
				e.printStackTrace();
			}
		}
			
	}
	
	/**
	 * This method uses Bellman Ford Equation to compute the minimum cost required to reach each router via its neighbors 
	 */
	public static void computeVectorsUsingBellmanFordEquation() {
		for(int i =0; i<neighborList.size();i++) {	//For all neighbors
			String neighborId = neighborList.get(i);
			int neighborIndex = idRowIndexMap.get(neighborId);
			//For all routers in the network
			for(int j=0;j<totalNumberOfRouters;j++) {
				String idOfJ = getKey(idRowIndexMap, j);
				if(idOfJ.equals(myId)) {				
					continue;
				}
				//Calculating minimum cost to reach all destination routers from current router.
				Double costToJViaNeighbor = routingTable[idRowIndexMap.get(myId)][neighborIndex] + routingTable[neighborIndex][j];
				if(i==0) {
					costToEachRouterMap.put(idOfJ, costToJViaNeighbor);
					hopRouteMap.put(idOfJ, neighborId);
				}else {
					if(costToEachRouterMap.get(idOfJ)> costToJViaNeighbor) {
						costToEachRouterMap.put(idOfJ, costToJViaNeighbor);
						hopRouteMap.put(idOfJ, neighborId);
					}
				}
			}
		}
	}
	
	/**
	 * 
	 */
	public static void printRoutingTable() {
	
		for(int i=0;i<totalNumberOfRouters;i++) {
			System.out.println();
			for(int j=0;j<totalNumberOfRouters ;j++) {
				if(routingTable[i][j].equals(max))
					System.out.print("-" +" ");
				else
					System.out.print(routingTable[i][j] +" ");
			}
		}
			
	}
	


		
		
}
