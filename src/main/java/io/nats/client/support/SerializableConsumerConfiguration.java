package io.nats.client.support;

import io.nats.client.api.ConsumerConfiguration;

import java.io.IOException;
import java.io.Serializable;

public class SerializableConsumerConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient ConsumerConfiguration cc;

    public SerializableConsumerConfiguration() {
        setConsumerConfiguration(ConsumerConfiguration.builder().build());
    }

    public SerializableConsumerConfiguration(ConsumerConfiguration cc) {
        setConsumerConfiguration(cc);
    }

    public SerializableConsumerConfiguration(ConsumerConfiguration.Builder builder) {
        setConsumerConfiguration(builder.build());
    }

    public void setConsumerConfiguration(ConsumerConfiguration cc) {
        this.cc = cc;
    }

    public ConsumerConfiguration getConsumerConfiguration() {
        return cc;
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(cc.toJson());
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        cc = ConsumerConfiguration.builder().json(in.readUTF()).build();
    }
}
