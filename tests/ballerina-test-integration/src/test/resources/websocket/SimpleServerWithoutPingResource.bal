import ballerina/io;
import ballerina/net.http;

endpoint http:ServiceEndpoint ep1 {
    port:9090
};

@http:WebSocketServiceConfig {
  basePath:"/test/without/ping/resource"
}
service<http: WebSocketService > SimpleProxyServer bind ep1{
    onOpen(endpoint conn) {
        io:println("New Client Connected");
    }
}
