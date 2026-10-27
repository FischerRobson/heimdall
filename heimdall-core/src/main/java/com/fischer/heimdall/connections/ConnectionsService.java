package com.fischer.heimdall.connections;

import com.fischer.heimdall.connections.detect.AgroalConnection;
import com.fischer.heimdall.connections.detect.HikariCPConnection;
import com.fischer.heimdall.connections.detect.KafkaConnection;
import com.fischer.heimdall.connections.detect.RabbitMQConnection;
import com.fischer.heimdall.connections.detect.RedisConnection;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import com.fischer.heimdall.tracing.HeimdallMonitor;

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
