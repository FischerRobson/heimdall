package connections;

import connections.detect.AgroalConnection;
import connections.detect.HikariCPConnection;
import connections.detect.KafkaConnection;
import connections.detect.RabbitMQConnection;
import connections.detect.RedisConnection;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import tracing.HeimdallMonitor;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor
@HeimdallMonitor
public class ConnectionsService {

    private final AgroalConnection agroalConnection;
    private final HikariCPConnection hikariCPConnection;
    private final KafkaConnection kafkaConnection;
    private final RedisConnection redisConnection;
    private final RabbitMQConnection rabbitMQConnection;

    public List<ConnectionInfo> detectConnections() {
        List<ConnectionInfo> connectionInfoList = new ArrayList<>();

        agroalConnection.detect(connectionInfoList);
        hikariCPConnection.detect(connectionInfoList);
        kafkaConnection.detect(connectionInfoList);
        redisConnection.detect(connectionInfoList);
        rabbitMQConnection.detect(connectionInfoList);


        return connectionInfoList;
    }

}
