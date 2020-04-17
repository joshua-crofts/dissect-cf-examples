package uk.ac.ljmu.fet.cs.cloud.examples.autoscaler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

public class CustomVI extends VirtualInfrastructure {

	/**
	 * The max hourly budget set by the user
	 */
	private static double hourlyBudget = 0.1;
	/**
	 * The current consumed budget for every hour
	 */
	private double consumedBudget = 0;
	/**
	 * The current budget for each 2 minute tick
	 */
	private double currentBudget = 0;
	/**
	 * The predetermined hourly rate for each VM
	 */
	private static double price = 0.09;
	/**
	 * The lower threshold utilisation bound for removing a VM
	 */
	private static double lowerThreshold = 0.1;
	/**
	 * The upper threshold utilisation bound for adding a VM
	 */
	private static double upperThreshold = 0.8;
	/**
	 * The max amount of VMs allowed for each type
	 */
	private static int maxVMs = 5;
	/**
	 * Current internal time of the autoscaler with each 1 = 2 minutes
	 */
	private int currentTime = 0;
	/**
	 * List of all currently running kinds
	 */
	private List<String> storedRunningKinds = new ArrayList<String>();

	/**
	 * Initialises the custom autoscaling mechanism
	 * 
	 * @param cloud the physical infrastructure to use to rent the VMs from
	 */
	public CustomVI(IaaSService cloud) {
		super(cloud);
		// TODO Auto-generated constructor stub
	}

	/**
	 * The auto scaling mechanism that runs every 2 minutes, assigns or removes VMs
	 * from each job type based on logic:
	 * 
	 * The overall hourly running priced is measured based on an estimated cost VM's
	 * are removed when the hourly cost is higher than the hourly budget
	 * 
	 * A threshold then decides based upon utilisation whether a new VM should be
	 * requested or destroyed
	 * 
	 * @param
	 */
	@Override
	public void tick(long fires) {
		// The basic setup for the autoscaler
		final Iterator<String> kinds = vmSetPerKind.keySet().iterator();
		while (kinds.hasNext()) {
			String kind = kinds.next();
			ArrayList<VirtualMachine> vmset = vmSetPerKind.get(kind);
			// First ensure that all jobs have at least one VM running
			if (vmset.isEmpty()) {
				//no VM for a kind. Add one and add its reference 
				//to the storedRunningKinds array
				requestVM(kind);
				storedRunningKinds.add(kind);
			}
			//check the hourly budget isn't being reached, if so
			//remove all but one of the VMs per kind
			if (hourlyBudget - consumedBudget <= 0) {
				//Ensure the array isnt empty
				if (!vmset.isEmpty()) {
					//remove all but the last VM from each kind
					for (int i = 1; i < vmset.size() - 1; i++) {
						VirtualMachine vm = vmset.get(vmset.size() - 1);
						destroyVM(vm);
					}
				}
				
			} else {
				//We check the thresholds to see if we need a new VM
				//or if a VM needs to be removed.
				int avgUtil = 0;
				//Calculate the average hourly utilisation of each assigned VM
				for (int i = 0; i < vmset.size(); i++) {
					VirtualMachine vm = vmset.get(i);
					avgUtil += getHourlyUtilisationPercForVM(vm);
				}
				//If hourly utilisation is above the threshold then add another VM
				if (avgUtil / vmset.size() > upperThreshold && vmset.size() < maxVMs) {
					requestVM(kind);
					storedRunningKinds.add(kind);
				} else {
					//If the utilisation is below the threshold check if we need to 
					//remove a VM
					for (int i = 0; i < vmset.size(); i++) {
						VirtualMachine vm = vmset.get(i);
						//Check that the VM we are looking at is already running
						if (vm.getState() == VirtualMachine.State.RUNNING) {
							//Check if the running VM is idle
							if (vm.underProcessing.isEmpty() && vm.toBeAdded.isEmpty()) {
								if (getHourlyUtilisationPercForVM(vm) < lowerThreshold) {
									//If the VM is idle and velow the lowerThreshold then destroy it
									//and remove it from the storedRunningKinds array
									destroyVM(vm);
									storedRunningKinds.remove(kind);
								}
							}
						}

					}

				}
			}

		}
		
		//Check current price for running the VM for 2 minutes
		//add this running price to the consumed budget
		try {
			currentBudget = (double)storedRunningKinds.size() /30* price;
			consumedBudget += currentBudget;
		} catch (NullPointerException e) {

		}
		
		//Check if an hour has passed
		if (currentTime == 30) {
			//Add the current months price to the consumedTotal
			//reset the 
			consumedTotal += consumedBudget;
			consumedBudget = 0;
			currentTime = 0;
			// System.out.println( "Hour");
		} 
		//Iterate the timer
		currentTime++;
	}

}
