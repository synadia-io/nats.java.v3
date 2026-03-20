package io.synadia.client.support;

import io.synadia.client.api.OrderedConsumerConfiguration;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;

public class SerializableOrderedConsumerConfiguration implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private transient OrderedConsumerConfiguration occ;

    public SerializableOrderedConsumerConfiguration() {
        setOrderedConsumerConfiguration(new OrderedConsumerConfiguration());
    }

    public SerializableOrderedConsumerConfiguration(OrderedConsumerConfiguration occ) {
        setOrderedConsumerConfiguration(occ);
    }

    public void setOrderedConsumerConfiguration(OrderedConsumerConfiguration occ) {
        this.occ = occ;
    }

    public OrderedConsumerConfiguration getOrderedConsumerConfiguration() {
        return occ;
    }

    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(occ.toJson());
    }

    @Serial
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        occ = new OrderedConsumerConfiguration(in.readUTF());
    }
}
