package fr.utbm.ia54.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import fr.utbm.ia54.consts.Const;
import fr.utbm.ia54.main.MainProgram;
import fr.utbm.ia54.path.CarPath;
import fr.utbm.ia54.utils.Functions;
import fr.utbm.ia54.utils.OrientedPoint;
import madkit.kernel.Agent;
import madkit.kernel.AgentAddress;
import madkit.kernel.Message;
import madkit.message.ObjectMessage;
import madkit.message.StringMessage;

/**
 * @author Alexis Florian
 * This agent control every car's trajectory and warn cars about collisions.
 */
public class Environment extends Agent{
	private HashMap<String, OrientedPoint> positions;
	private HashMap<String, AgentAddress> addresses;
	private List<List<String>> carsId; // List of car's networkId (one list by train)

	/**
	 * This is the first activated behavior in the life cycle of a MaDKit agent.
	 */
	@Override
	protected void activate() {
		// Initialization
		positions = new HashMap<String, OrientedPoint>();
		addresses = new HashMap<String, AgentAddress>();
		carsId = new ArrayList<List<String>>();
		String group = new String();
		
		for(int i=0; i<Const.NB_TRAIN;i++) {
			group = Const.SIMU_GROUP+i;
			requestRole(Const.MY_COMMUNITY, group, Const.ENV_ROLE);
			carsId.add(new ArrayList<String>());
		}
	}

	@Override
    protected void live() {
		HashMap<OrientedPoint, List<String>> map = new HashMap<OrientedPoint, List<String>>(); // List of groups on a crossing
		int nb = 0;
		
		// Waiting for car initialization
		while(nb != carsId.size()*Const.NB_CAR_BY_TRAIN) {
			getNewMessages();
			nb = 0;
			for(int i=0; i<carsId.size();i++){
				nb += carsId.get(i).size();
			}
		}
		
		while(true) {
			getNewMessages();
			if(!carsId.isEmpty()){
				CarPath carPath = MainProgram.getCarPath();
				// For all crossing point
				for(OrientedPoint cross : carPath.getCrossing()){
					// Get the trains which are on the cross
					List<String> groups = map.get(cross);
					if(groups == null){
						groups = new ArrayList<>();
					}

					// For all trains
					for(int i=0; i<carsId.size();i++){
						// Get informations about the first car in the group
						String carGroup = Const.SIMU_GROUP + String.valueOf(i);
						String firstCarId = carsId.get(i).get(0);
						OrientedPoint carPos = positions.get(firstCarId);
						
						// If the first car is near a cross and if her train isn't registered for the cross
						if(Functions.manhattan(carPos,cross) < 300 && !groups.contains(carGroup)){
							// If there is already an other train
							if(!groups.isEmpty()){
								// Get the list of cars which doesn't reach the cross
								LinkedList<OrientedPoint> cars = new LinkedList<OrientedPoint>();
								cars.add(cross);
								
								// List of cars which doesn't reach the cross (other train)
								int otherGroup = (i==0?1:0); // Warning, work only for 2 trains
								@SuppressWarnings("unused")
								String otherGroupStr = String.valueOf(otherGroup);
								String otherCarId = "";
								OrientedPoint otherCarPos = null;
								boolean firstUnreached = false;
								
								for(int j=0; j<carsId.get(otherGroup).size();j++){
									otherCarId = carsId.get(otherGroup).get(j);
									otherCarPos = positions.get(otherCarId);
									// If the cross is unreached
									if(!crossPassedBis(otherCarPos,cross))
										firstUnreached = true;
									// Add all the cars that have unreached the cross
									if(firstUnreached)
										cars.add(otherCarPos);
								}
								
								// Send a message to the train to slow down near the cross (crossing point, list of cars which doesn't reach the cross)
								ObjectMessage<LinkedList<OrientedPoint>> msg = new ObjectMessage<LinkedList<OrientedPoint>>(cars);
								sendMessage(Const.MY_COMMUNITY, carGroup, Const.TRAIN_ROLE, msg);
							}
							
							// Register the group for the cross
							groups.add(carGroup);
							map.put(cross,groups);
						}
						
						// Get informations about the last car in the group
						String lastCarId = carsId.get(i).get(carsId.get(i).size()-1);
						carPos = positions.get(lastCarId);

						// Detect when the last car leave the cross
						if(Functions.manhattan(carPos,cross) > 10 && groups.contains(carGroup) && crossPassed(carPos,cross)){
							// Unregister the group for the cross
							groups.remove(groups.indexOf(carGroup));
						}
					}
				}
			}
		}
	}
	
	@Override
    protected void end() {
		// Nothing to do
	}
	
	/**
	 * Check for new messages about agent's positions
	 */
	private void getNewMessages() {
		Message m = null;
		
		// we manage all messages
		while (!isMessageBoxEmpty()){
			m = nextMessage();
			
			if (m instanceof ObjectMessage) {
				@SuppressWarnings("unchecked")
				ObjectMessage<HashMap<String, OrientedPoint>> message = (ObjectMessage<HashMap<String, OrientedPoint>>) m;
				HashMap<String, OrientedPoint> tmp = message.getContent();
				positions.putAll(tmp);
				// substring to convert agent network id to network id
				addresses.put(message.getSender().getAgentNetworkID().substring(0, message.getReceiver().getAgentNetworkID().length()-2), message.getSender());
				
				String address = message.getSender().getAgentNetworkID();
				String group = message.getSender().getGroup();
				if(!carsId.get(getNumTrain(group)).contains(address.substring(0,address.length()-2))){
					carsId.get(getNumTrain(group)).add(address.substring(0,address.length()-2));
				}
			}
		}
	}
	
	public HashMap<String, OrientedPoint> inRange(OrientedPoint target, int range, HashMap<String, OrientedPoint> exclus) {
		return inRange(target, range, positions, exclus);
	}
		
	
	/**
	 * 
	 * Search for oriented points within rand distance of target.
	 * Population contain potential targets and exclus targets not to select
	 * @param target
	 * @param range
	 * @param population
	 * @param exclus
	 * @return
	 */
	public HashMap<String, OrientedPoint> inRange(OrientedPoint target, int range, HashMap<String, OrientedPoint> population, HashMap<String, OrientedPoint> exclus) {
		
		if(!positions.equals(null)) {
			HashMap<String, OrientedPoint> voisins = new HashMap<String, OrientedPoint>();
			float distance = 0;
			
			for(String ad : population.keySet()) {
				distance = Functions.manhattan(population.get(ad), target);
				if(distance < range && distance > 0 && !exclus.containsKey(ad)) {
					voisins.put(ad, population.get(ad));
				}
			}
			return voisins;
		}
		return null;
	}
	
	public void sendMessageToId(String netwId, String message) {
		AgentAddress addr = addresses.get(netwId);
		if (addr != null) {
			sendMessage(addr, new StringMessage(message));
		}
	}
	
	/**
	 * Get the number of the train
	 * @param group
	 * @return
	 */
	public int getNumTrain(String group) {
		char c = group.charAt(group.length()-1);
		return Character.getNumericValue(c);
	}
	
	/**
	 * Used for the last car in a train
	 * @param car
	 * @param cross
	 * @return
	 */
	public boolean crossPassed(OrientedPoint car, OrientedPoint cross){
		if(Math.toDegrees(car.getAngle()) == 90){ 			// Right
			if(car.y == cross.y)
				return (car.x > cross.x) ? true:false;
		}
		else if(Math.toDegrees(car.getAngle()) == 180){ 	// Down
			if(car.x == cross.x)
				return (car.y > cross.y) ? true:false;
		}
		else if(Math.toDegrees(car.getAngle()) == 270){ 	// Left
			if(car.y == cross.y)
				return (car.x < cross.x) ? true:false;
		}
		else{ 												// Up
			if(car.x == cross.x)
				return (car.y < cross.y) ? true:false;
		}
		return false;
	}
	
	/**
	 * To detect which cars passed the cross
	 * @param car
	 * @param cross
	 * @return
	 */
	public boolean crossPassedBis(OrientedPoint car, OrientedPoint cross){
		if(Math.toDegrees(car.getAngle()) == 90){ 			// Right
			return (car.x > cross.x) ? true:false;
		}
		else if(Math.toDegrees(car.getAngle()) == 180){ 	// Down
			return (car.y > cross.y) ? true:false;
		}
		else if(Math.toDegrees(car.getAngle()) == 270){ 	// Left
			return (car.x < cross.x) ? true:false;
		}
		else{ 												// Up
			return (car.y < cross.y) ? true:false;
		}
	}
}