package pl.mkapiczynski.dron.business;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.websocket.EncodeException;
import javax.websocket.Session;

import org.jboss.logging.Logger;

import pl.mkapiczynski.dron.database.CSTUser;
import pl.mkapiczynski.dron.database.Drone;
import pl.mkapiczynski.dron.database.DroneSession;
import pl.mkapiczynski.dron.database.Location;
import pl.mkapiczynski.dron.domain.GeoPoint;
import pl.mkapiczynski.dron.message.ClientGeoDataMessage;
import pl.mkapiczynski.dron.message.ClientLoginMessage;
import pl.mkapiczynski.dron.message.Message;

@Local
@Stateless(name = "ClientDeviceService")
public class ClientDeviceServiceBean implements ClientDeviceService {

	private static final Logger log = Logger.getLogger(ClientDeviceServiceBean.class);

	@Inject
	private SearchedAreaService searchedAreaService;

	@Inject
	private DroneService droneService;

	@Override
	public void handleClientLoginMessage(Message incomingMessage, Session session, Set<Session> clientSessions) {
		ClientLoginMessage clientLoginMessage = (ClientLoginMessage) incomingMessage;
		Long clientId = clientLoginMessage.getClientId();
		if (clientDeviceHasNotRegisteredSession(session, clientSessions)) {
			session.getUserProperties().put("clientId", clientId);
			clientSessions.add(session);
			System.out.println("New clientDevice with id: " + clientId);
		} else {
			System.out.println("Client device with id: " + clientId + " already registered");
		}
	}

	@Override
	public void sendGeoDataToAllSessionRegisteredClients(Drone drone, Set<Session> clientSessions) {
		ClientGeoDataMessage geoMessage = generateClientGeoDataMessage(drone);
		Iterator<Session> iterator = clientSessions.iterator();
		while (iterator.hasNext()) {
			Session currentClient = iterator.next();
			if (clientIsAssignedToDrone(currentClient, drone)) {
				try {
					currentClient.getAsyncRemote().sendObject(geoMessage);
					log.info("Message send to client : " + currentClient.getUserProperties().get("clientId"));
				} catch (IllegalArgumentException e) {
					log.error("Illegal argument exception while sending a message to client: "
							+ currentClient.getUserProperties().get("clientId") + " : " + e);
				}
			}
		}
	}
	


	private ClientGeoDataMessage generateClientGeoDataMessage(Drone drone) {
		ClientGeoDataMessage clientGeoDataMessage = new ClientGeoDataMessage();
		clientGeoDataMessage.setDeviceId(drone.getDroneId());
		clientGeoDataMessage.setDeviceType("GPSTracker");
		clientGeoDataMessage.setLastPosition(new GeoPoint(drone.getLastLocation().getLatitude(),
				drone.getLastLocation().getLongitude(), drone.getLastLocation().getAltitude()));
		clientGeoDataMessage.setTimestamp(new Date());
		DroneSession activeSession = droneService.getActiveDroneSession(drone);
		if (activeSession != null) {
			if (activeSession.getSearchedArea() != null
					&& activeSession.getSearchedArea().getSearchedLocations() != null) {
				List<GeoPoint> searchedArea = convertLocationSearchedAreaToGeoPointSearchedArea(
						activeSession.getSearchedArea().getSearchedLocations());
				clientGeoDataMessage.setSearchedArea(searchedArea);
			}
			if (activeSession.getLastSearchedArea() != null
					&& activeSession.getLastSearchedArea().getSearchedLocations() != null) {
				List<GeoPoint> lastSearchedArea = convertLocationSearchedAreaToGeoPointSearchedArea(
						activeSession.getLastSearchedArea().getSearchedLocations());
				clientGeoDataMessage.setLastSearchedArea(lastSearchedArea);
			}
		}
		return clientGeoDataMessage;
	}

	private boolean clientDeviceHasNotRegisteredSession(Session session, Set<Session> clientSessions) {
		if (!clientSessions.contains(session)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * TODO Do usunięcia, taka sama metoda w GPSTraxckesServiceBean
	 */
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
	
	private boolean clientIsAssignedToDrone(Session clientSession, Drone drone){
		boolean clientIsAssignedToDrone = false;
		for (int i = 0; i < drone.getAssignedUsers().size(); i++) {
			CSTUser userClient = drone.getAssignedUsers().get(i);
			if (userClient.getUserId() == clientSession.getUserProperties().get("clientId")) {
				clientIsAssignedToDrone = true;
				break;
			}
		}
		return clientIsAssignedToDrone;
	}

}