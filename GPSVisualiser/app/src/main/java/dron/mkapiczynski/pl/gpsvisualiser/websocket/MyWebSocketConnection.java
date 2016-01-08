package dron.mkapiczynski.pl.gpsvisualiser.websocket;

import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;

import org.osmdroid.bonuspack.overlays.Polygon;
import org.osmdroid.util.GeoPoint;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.json.Json;

import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;
import dron.mkapiczynski.pl.gpsvisualiser.activity.VisualizeActivity;
import dron.mkapiczynski.pl.gpsvisualiser.decoder.MessageDecoder;
import dron.mkapiczynski.pl.gpsvisualiser.domain.Drone;
import dron.mkapiczynski.pl.gpsvisualiser.domain.MyGeoPoint;
import dron.mkapiczynski.pl.gpsvisualiser.message.ClientLoginMessage;
import dron.mkapiczynski.pl.gpsvisualiser.message.GeoDataMessage;

/**
 * Created by Miix on 2016-01-08.
 */
public class MyWebSocketConnection extends WebSocketConnection {
    private static final String TAG = MyWebSocketConnection.class.getSimpleName();
    private static final String SERVER = "ws://0.tcp.ngrok.io:46994/dron-server-web/server";
    private VisualizeActivity activity;
    private boolean deviceIsLoggedIn = false;

    public MyWebSocketConnection(VisualizeActivity activity){
        super();
        this.activity = activity;
    }

    public void connectToWebSocketServer() {
        try {
            connect(SERVER, new WebSocketHandler() {
                @Override
                public void onOpen() {
                    Log.d("WEBSOCKETS", "Connected to server");
                    if(sendLoginMessage()){
                        deviceIsLoggedIn=true;
                    }
                    Toast.makeText(activity.getApplicationContext(), "You are now connected to the server", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onTextMessage(String jsonMessage) {
                    String messageType = Json.createReader(new StringReader(jsonMessage)).readObject().getString("messageType");
                    if ("ClientGeoDataMessage".equals(messageType)) {
                        GeoDataMessage geoMessage = MessageDecoder.decodeGeoDataMessage(jsonMessage);
                        MyGeoPoint point = geoMessage.getLastPosition();
                        GeoPoint currentDronePosition = new GeoPoint(point.getLatitude(), point.getLongitude(),point.getAltitude());
                        List<GeoPoint> searchedArea = new ArrayList<>();
                        for(int i=0; i<geoMessage.getSearchedArea().size();i++){
                            searchedArea.add(new GeoPoint(geoMessage.getSearchedArea().get(i).getLatitude(), geoMessage.getSearchedArea().get(i).getLongitude()));
                        }

                        Drone drone = new Drone();
                        drone.setDeviceId(geoMessage.getDeviceId());
                        drone.setCurrentPosition(currentDronePosition);
                        if(drone.getSearchedArea()!=null) {
                            drone.getSearchedArea().addAll(searchedArea);
                        } else {
                            drone.setSearchedArea(new HashSet<GeoPoint>());
                            drone.getSearchedArea().addAll(searchedArea);
                        }

                        drone.setLastSearchedArea(searchedArea);

                        activity.updateDronesOnMap(drone);
                    }
                }


                @Override
                public void onClose(int code, String reason) {
                    Toast.makeText(activity.getApplicationContext(), "Connection closed Code:" + code + " Reason: " + reason, Toast.LENGTH_LONG).show();
                    Log.d("WEBSOCKETS", "Connection closed Code:" + code + " Reason: " + reason);
                    deviceIsLoggedIn=false;
                    //startReestablishingConnectionScheduler();
                }
            });
        } catch (WebSocketException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private boolean sendLoginMessage() {
        ClientLoginMessage clientLoginMessage = new ClientLoginMessage();
        clientLoginMessage.setClientId("Client2");
        Gson gson = new Gson();
        if (isConnected()) {
            sendTextMessage(gson.toJson(clientLoginMessage));
            return true;
        } else{
            Log.i(TAG, "Coudn't send a login message. No connection with server");
            return false;
        }
    }

}