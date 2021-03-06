package pl.mkapiczynski.dron.business;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.jboss.logging.Logger;

import pl.mkapiczynski.dron.database.Drone;
import pl.mkapiczynski.dron.database.DroneSession;
import pl.mkapiczynski.dron.database.DroneSessionStatusEnum;
import pl.mkapiczynski.dron.database.DroneStatusEnum;
import pl.mkapiczynski.dron.database.HoleInSearchedArea;
import pl.mkapiczynski.dron.database.Location;
import pl.mkapiczynski.dron.database.SearchedArea;
import pl.mkapiczynski.dron.domain.GeoPoint;
import pl.mkapiczynski.dron.domain.NDBHoleInSearchedArea;
import pl.mkapiczynski.dron.domain.NDBSearchedArea;

/**
 * Klasa biznesowa do obsługi operacji bazodanowych związanych z dronami
 * 
 * @author Michal Kapiczynski
 *
 */
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
	public boolean droneHasActiveSession(Long droneId) {
		Drone droneToCheck = getDroneById(droneId);
		if (droneToCheck != null) {
			DroneSession droneSession = getActiveDroneSession(droneToCheck);
			if (droneSession != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean createNewDroneSession(Long droneId) {
		Drone drone = getDroneById(droneId);
		if (drone != null) {
			closePreviousActiveSession(drone);
			DroneSession droneSession = new DroneSession();
			drone.setStatus(DroneStatusEnum.ONLINE);
			SearchedArea searchedArea = new SearchedArea();
			SearchedArea lastSearchedArea = new SearchedArea();
			droneSession.setDrone(drone);
			droneSession.setSearchedArea(searchedArea);
			droneSession.setLastSearchedArea(lastSearchedArea);
			droneSession.setSessionStarted(new Date());
			droneSession.setStatus(DroneSessionStatusEnum.ACTIVE);
			drone.setActiveSession(droneSession);
			entityManager.persist(droneSession);
			return true;
		} else {
			log.info("No drone with id: " + droneId + " was found");
			return false;
		}
	}

	@Override
	public void updateDroneSearchedArea(Drone drone) {
		log.debug("Method updateDroneSearchedArea started: " + new Date());
		DroneSession activeSession = getActiveDroneSession(drone);
		if (activeSession != null) {
			SearchedArea recentSearchedArea = searchedAreaService.calculateSearchedArea(drone.getLastLocation(), drone.getCameraAngle());
			SearchedArea lastSearchedArea = activeSession.getLastSearchedArea();
			SearchedArea currentSearchedArea = activeSession.getSearchedArea();
			
			if (lastSearchedArea != null) {
				if(lastSearchedArea.getSearchedLocations()!=null && !lastSearchedArea.getSearchedLocations().isEmpty()){
					searchedAreaService.updateSearchedArea(currentSearchedArea, lastSearchedArea);
					searchedAreaService.updateSearchedAreaHoles(currentSearchedArea, lastSearchedArea, recentSearchedArea);
				}
				lastSearchedArea.setSearchedLocations(recentSearchedArea.getSearchedLocations());
				lastSearchedArea.setHolesInSearchedArea(recentSearchedArea.getHolesInSearchedArea());
			} else if (lastSearchedArea == null && recentSearchedArea != null) {
				lastSearchedArea = new SearchedArea();
				lastSearchedArea.setSearchedLocations(recentSearchedArea.getSearchedLocations());
				lastSearchedArea.setHolesInSearchedArea(recentSearchedArea.getHolesInSearchedArea());
			}
		} else {
			log.info("No active session for drone with id: " + drone.getDroneId());
		}
		log.debug("Method updateDroneSearchedArea ended: " + new Date());
	}

	@Override
	public void closeDroneActiveSession(Long droneId) {
		Drone drone = getDroneById(droneId);
		if (drone != null) {
			DroneSession activeSession = getActiveDroneSession(drone);
			if (activeSession != null) {
				activeSession.setStatus(DroneSessionStatusEnum.FINISHED);
				activeSession.setSessionEnded(new Date());
			}
			drone.setActiveSession(null);
			drone.setStatus(DroneStatusEnum.OFFLINE);
		} else {
			log.info("No drone with id: " + droneId + " was found");
		}

	}

	@Override
	public DroneSession getActiveDroneSession(Drone drone) {
		DroneSession activeDroneSession = drone.getActiveSession();
		return activeDroneSession;
	}

	private void closePreviousActiveSession(Drone drone) {
		DroneSession activeSession = getActiveDroneSession(drone);
		if (activeSession != null) {
			activeSession.setStatus(DroneSessionStatusEnum.FINISHED);
			activeSession.setSessionEnded(new Date());
		}
	}


	@Override
	public List<DroneSession> getDroneSessions(Long droneId) {
		List<DroneSession> droneSessions = null;
		String queryStr = "SELECT d FROM DroneSession d WHERE d.drone.id like :droneId AND d.sessionStarted!=NULL AND d.sessionEnded!=null ORDER BY d.sessionStarted DESC";
		TypedQuery<DroneSession> query = entityManager.createQuery(queryStr, DroneSession.class);
		query.setParameter("droneId", droneId);
		query.setMaxResults(10);
		droneSessions = query.getResultList();
		if (droneSessions != null) {
			return droneSessions;
		}
		return new ArrayList<>();
	}

	@Override
	public NDBSearchedArea getSearchedAreaForSession(Long sessionId) {
		NDBSearchedArea ndbSearchedArea = new NDBSearchedArea();
		String queryStr = "SELECT s FROM DroneSession s WHERE s.sessionId = :sessionId";
		TypedQuery<DroneSession> query = entityManager.createQuery(queryStr, DroneSession.class);
		query.setParameter("sessionId", sessionId);
		List<DroneSession> droneSessions = query.getResultList();
		DroneSession resultSession = new DroneSession();
		if (droneSessions != null && !droneSessions.isEmpty()) {
			resultSession = droneSessions.get(0);
		}
		SearchedArea searchedArea = resultSession.getSearchedArea();
		List<GeoPoint> responseSearchedArea = new ArrayList<>();
		if(searchedArea!=null && searchedArea.getSearchedLocations()!=null && !searchedArea.getSearchedLocations().isEmpty()){
			responseSearchedArea = convertLocationSearchedAreaToGeoPointSearchedArea(searchedArea.getSearchedLocations());
		}
		List<NDBHoleInSearchedArea> responseSearchedAreaHoles = new ArrayList<>();
		if(searchedArea!=null && searchedArea.getHolesInSearchedArea()!=null && !searchedArea.getHolesInSearchedArea().isEmpty()){
			for(int i=0; i<searchedArea.getHolesInSearchedArea().size();i++){
				HoleInSearchedArea hole = searchedArea.getHolesInSearchedArea().get(i);
				NDBHoleInSearchedArea ndbHole = new NDBHoleInSearchedArea();
				List<GeoPoint> holesLocations = convertLocationSearchedAreaToGeoPointSearchedArea(hole.getHoleLocations());
				ndbHole.setHoleLocations(holesLocations);
				responseSearchedAreaHoles.add(ndbHole);
			}
		}
		ndbSearchedArea.setSearchedAreaLocations(responseSearchedArea);
		ndbSearchedArea.setHolesInSearchedArea(responseSearchedAreaHoles);
		

		return ndbSearchedArea;
	}
	
	private static List<GeoPoint> convertLocationSearchedAreaToGeoPointSearchedArea(
			List<Location> locationSearchedArea) {
		List<GeoPoint> geoPointSearchedArea = new ArrayList<>();
		for (int i = 0; i < locationSearchedArea.size(); i++) {
			GeoPoint geoP = new GeoPoint();
			geoP.setLatitude(locationSearchedArea.get(i).getLatitude());
			geoP.setLongitude(locationSearchedArea.get(i).getLongitude());
			geoPointSearchedArea.add(geoP);
		}
		return geoPointSearchedArea;
	};

}
