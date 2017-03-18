package ramble;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.websocket.WebSocket;
import ratpack.websocket.WebSocketClose;
import ratpack.websocket.WebSocketHandler;
import ratpack.websocket.WebSocketMessage;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handles livereload websocket messages <a href="http://livereload.com/"></a> and
 * sends reload commands for the api-console.
 */
class LivereloadHandler implements WebSocketHandler<String> {
    private final static Logger LOG = LoggerFactory.getLogger(LivereloadHandler.class);

    private final FileWatcher fileWatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConcurrentMap<String, WebSocket> webSockets = new ConcurrentHashMap<>();

    public LivereloadHandler(final Context ctx, final Path ramlFile) {
        this.fileWatcher = ctx.get(FileWatcher.class);
    }

    private void reload(final WebSocket webSocket, final FileTime fileTime) {
        send(webSocket, new ReloadMessage("api-console.js"));
    }

    @Override
    public String onOpen(final WebSocket webSocket) throws Exception {
        final String clientId = UUID.randomUUID().toString();
        webSockets.put(clientId, webSocket);

        return clientId;
    }

    @Override
    public void onClose(final WebSocketClose<String> close) throws Exception {
        final String clientId = close.getOpenResult();

        webSockets.remove(clientId);
    }

    @Override
    public void onMessage(final WebSocketMessage<String> frame) throws Exception {
        final Message request = objectMapper.readValue(frame.getText(), Message.class);
        final WebSocket connection = frame.getConnection();
        respond(frame, request).ifPresent(response -> send(connection, response));
    }

    private void send(final WebSocket webSocket, final Message message) {
        try {
            webSocket.send(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            LOG.error("Error sending response", e);
        }
    }

    private Optional<Message> respond(final WebSocketMessage<String> frame, final Message request) {
        switch (request.command) {
            case "hello":
                final HelloMessage helloRequest = (HelloMessage) request;
                final HelloMessage helloResponse = new HelloMessage();
                helloResponse.setServerName("Ramble server");
                helloResponse.setProtocols(helloRequest.protocols);

                this.fileWatcher.lastModified().then(fileTime -> reload(frame.getConnection(), fileTime));

                return Optional.of(helloResponse);
            default:
                return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(AlertMessage.class),
            @JsonSubTypes.Type(HelloMessage.class),
            @JsonSubTypes.Type(InfoMessage.class)})
    @JsonTypeInfo(visible = true, property = "command", include = JsonTypeInfo.As.PROPERTY, use = JsonTypeInfo.Id.NAME)
    private static abstract class Message {
        String command;

        public String getCommand() {
            return command;
        }

        public void setCommand(final String command) {
            this.command = command;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("hello")
    private static class HelloMessage extends Message {
        List<String> protocols;
        String ver;
        String serverName;

        public List<String> getProtocols() {
            return protocols;
        }

        public void setProtocols(final List<String> protocols) {
            this.protocols = protocols;
        }

        public String getVer() {
            return ver;
        }

        public void setVer(final String ver) {
            this.ver = ver;
        }

        public String getServerName() {
            return serverName;
        }

        public void setServerName(final String serverName) {
            this.serverName = serverName;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("alert")
    private static class AlertMessage extends Message {
        String message;

        public AlertMessage(final String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(final String message) {
            this.message = message;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("reload")
    private static class ReloadMessage extends Message {
        String path;

        public ReloadMessage(final String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        public void setPath(final String path) {
            this.path = path;
        }
    }

    @JsonTypeName("info")
    private static class InfoMessage extends Message {
    }
}
