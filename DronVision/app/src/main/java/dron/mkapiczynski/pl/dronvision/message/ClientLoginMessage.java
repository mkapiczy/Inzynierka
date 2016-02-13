package dron.mkapiczynski.pl.dronvision.message;

/**
 * Created by Miix on 2016-01-05.
 */
public class ClientLoginMessage {
    private static final String MESSAGE_TYPE = "ClientLoginMessage";
    private final String messageType = MESSAGE_TYPE;
    private Long clientId;


    public String getMessageType() {
        return messageType;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    @Override
    public String toString() {
        return "TrackerLoginMessage [messageType=" + messageType + ", clientId=" + clientId + "]";
    }


}
