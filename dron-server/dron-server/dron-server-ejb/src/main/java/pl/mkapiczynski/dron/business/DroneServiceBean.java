package pl.mkapiczynski.dron.business;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.jboss.logging.Logger;

import pl.mkapiczynski.dron.database.Drone;
import pl.mkapiczynski.dron.database.DroneSession;
import pl.mkapiczynski.dron.database.DroneSessionStatus;
import pl.mkapiczynski.dron.database.DroneStatusEnum;
import pl.mkapiczynski.dron.database.Location;
import pl.mkapiczynski.dron.database.SearchedArea;
import pl.mkapiczynski.dron.domain.GeoPoint;

@Local
@Stateless(name = "DroneService")
public class DroneServiceBean implements DroneService {
	private static final Logger log = Logger.getLogger(DroneServiceBean.class);
	
	@PersistenceContext(name = "dron")
	private EntityManager entityManager;
	
	@Inject
	private SearchedAreaService searchedAreaService;

	@Override
	public Drone getDroneById(Long droneId) {
		Drone drone = entityManager.find(Drone.class, droneId);
		return drone;
	}

	@Override
	public boolean createNewDroneSession(Long droneId) {
		Drone drone = getDroneById(droneId);
		if (drone != null) {
			drone.setStatus(DroneStatusEnum.ONLINE);
			SearchedArea searchedArea = new SearchedArea();
			DroneSession droneSession = new DroneSession();
			SearchedArea lastSearchedArea = new SearchedArea();
			droneSession.setDrone(drone);
			droneSession.setSearchedArea(searchedArea);
			droneSession.setLastSearchedArea(lastSearchedArea);
			droneSession.setSessionStarted(new Date());
			droneSession.setStatus(DroneSessionStatus.ACTIVE);
			drone.setActiveSession(droneSession);
			entityManager.persist(droneSession);
			return true;
		} else {
			log.info("No drone with id: " + droneId + " was found");
			return false;
		}
	}

	@Override
	public void updateDroneSearchedArea(Drone drone, Location newSearchedLocation) {
		if (drone != null && newSearchedLocation != null) {
			DroneSession activeSession = getActiveDroneSession(drone);
			if (activeSession != null) {
				SearchedArea recentSearchedArea = searchedAreaService.calculateSearchedArea(newSearchedLocation);
				SearchedArea lastSearchedArea = activeSession.getLastSearchedArea();
				SearchedArea currentSearchedArea = activeSession.getSearchedArea();
				
				if(lastSearchedArea!=null){
					searchedAreaService.updateSearchedArea(currentSearchedArea, lastSearchedArea);
					lastSearchedArea.setSearchedLocations(recentSearchedArea.getSearchedLocations());
				} else if(lastSearchedArea==null && recentSearchedArea!=null){
					lastSearchedArea = new SearchedArea();
					lastSearchedArea.setSearchedLocations(recentSearchedArea.getSearchedLocations());
				}
			}
		} else {
			log.info("No drone with id: " + drone.getDroneId() + " was found");
		}
	}

	@Override
	public void closeDroneSession(Long droneId) {
		Drone drone = getDroneById(droneId);
		if(drone!=null){
			DroneSession activeSession = getActiveDroneSession(drone);
			activeSession.setStatus(DroneSessionStatus.FINISHED);
			activeSession.setSessionEnded(new Date());
			drone.setStatus(DroneStatusEnum.OFFLINE);
		} else{
			log.info("No drone with id: " + droneId + " was found");
		}

	}

	@Override
	public DroneSession getActiveDroneSession(Drone drone) {
		List<DroneSession> droneSessions = drone.getSessions();
		DroneSession activeSession = null;
		for (int i = 0; i < droneSessions.size(); i++) {
			if (droneSessions.get(i).getStatus().equals(DroneSessionStatus.ACTIVE)) {
				activeSession = droneSessions.get(i);
			}
		}
		return activeSession;
	}
	
	private List<Location> convertGeoPointSearchedAreaToLocationSearchedArea(List<GeoPoint> geoPointSearchedArea) {
		List<Location> locationSearchedArea = new ArrayList();
		for(int i=0; i<geoPointSearchedArea.size();i++){
			GeoPoint tempPoint = geoPointSearchedArea.get(i);
			Location loc = new Location();
			loc.setLatitude(tempPoint.getLatitude());
			loc.setLongitude(tempPoint.getLongitude());
			loc.setAltitude(tempPoint.getAltitude());
			locationSearchedArea.add(loc);
		}
		return locationSearchedArea;
	}

}