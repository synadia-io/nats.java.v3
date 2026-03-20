package io.synadia.client.support;

import io.synadia.client.api.ConsumerConfiguration;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;

public class SerializableConsumerConfiguration implements Serializable {
    @Serial
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

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(cc.toJson());
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        cc = ConsumerConfiguration.builder().json(in.readUTF()).build();
    }
}
