package io.nats.client.support;

import io.nats.client.ConsumeOptions;

import java.io.IOException;
import java.io.Serializable;

import static io.nats.client.ConsumeOptions.DEFAULT_CONSUME_OPTIONS;

public class SerializableConsumeOptions implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient ConsumeOptions co;

    public SerializableConsumeOptions() {
        setConsumeOptions(DEFAULT_CONSUME_OPTIONS);
    }

    public SerializableConsumeOptions(ConsumeOptions co) {
        setConsumeOptions(co);
    }

    public SerializableConsumeOptions(ConsumeOptions.Builder builder) {
        setConsumeOptions(builder.build());
    }

    public void setConsumeOptions(ConsumeOptions co) {
        this.co = co;
    }

    public ConsumeOptions getConsumeOptions() {
        return co;
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(co.toJson());
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        co = ConsumeOptions.builder().json(in.readUTF()).build();
    }
}
