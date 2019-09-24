/*Aarti Nimhan (801098198)*/
/**
 * @author Aarti
 *
 */
public class SenderThread implements Runnable {

	@Override
	public void run() {
		while (true) {
			try {
					CreateRouter.updateRoutingTableFromFile();
					CreateRouter.printOutput();
					CreateRouter.sendUpdateToNeighbors();
					CreateRouter.computeVectorsUsingBellmanFordEquation();
					//Waiting for 15 seconds before sending update to neighbors.
					Thread.sleep(15000);
					
			} catch (Exception e) {
					System.out.println("Exception in readFileChanges and sending to neighbors.");
					e.printStackTrace();
			}
		}
	}
}
