package fr.trendev.boundaries;

import fr.trendev.controllers.HazelcastReplicatedMap;
import fr.trendev.controllers.RecordsManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 *
 * @author
 */
@Path("/")
@Stateless
public class HelloWorldService {

    private static final Logger LOG = Logger.getLogger(HelloWorldService.class.getName());

    private String message;
    private String podName;
    private String namespace;
    private String podIP;

    private final long maxMem;

    @Inject
//    @ClusteredSingleton
    @HazelcastReplicatedMap
    private RecordsManager recordsManager;

    public HelloWorldService() {
        this.maxMem = Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    @PostConstruct
    protected void init() {
        Config config = ConfigProvider.getConfig();
        this.message = config.getOptionalValue("TEXT_MESSAGE", String.class)
                .orElse("helloworld");

        // fields will be omitted if null
        this.podName = config.getOptionalValue("MY_POD_NAME", String.class)
                .orElseGet(() -> {
                    String hostname = null;
                    try {
                        hostname = InetAddress.getLocalHost().getHostName();
                    } catch (UnknownHostException ex) {
                    }
                    return hostname;
                });
        this.namespace = config.getOptionalValue("MY_POD_NAMESPACE", String.class)
                .orElse(null);
        this.podIP = config.getOptionalValue("MY_POD_IP", String.class)
                .orElseGet(() -> {
                    String ip = null;
                    try {
                        ip = InetAddress.getLocalHost().getHostAddress();
                    } catch (UnknownHostException ex) {
                    }
                    return ip;
                });

        LOG.log(Level.INFO, "{0} initialized",
                HelloWorldService.class.getSimpleName());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response message() {
        
        long start = System.nanoTime();

        List<String> records = this.recordsManager.add(podName);
        
        long time = System.nanoTime() - start;

        long podsInRecords = records.stream()
                .distinct()
                .count();

        JsonObjectBuilder job = Json.createObjectBuilder();

        job.add("message", message)
                .add("records", Json.createArrayBuilder(records).build())
                .add("records_length", records.size())
                .add("pods_in_records", podsInRecords);

        //optional values
        this.jsonBuilderHelper(job, "pod_name", podName);
        this.jsonBuilderHelper(job, "namespace", namespace);
        this.jsonBuilderHelper(job, "pod_IP", podIP);

        job.add("max_heap_MB", this.maxMem);
        job.add("time_ns",time);
        job.add("timestamp_ms", new Date().getTime());

        JsonObject jo = job.build();

        LOG.info(jo.toString());

        return Response.ok(jo).build();
    }

    private void jsonBuilderHelper(JsonObjectBuilder job,
            String key,
            String value) {
        if (value != null && !value.isEmpty()) {
            job.add(key, value);
        }
    }
}
