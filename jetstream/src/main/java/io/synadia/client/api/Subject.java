package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An object representing a stream's subject and the count of its messages
 */
@NullMarked
public class Subject implements Comparable<Subject> {
    private final String name;
    private final long count;

    static List<Subject> listOf(@Nullable LazyJsonValue v) {
        List<Subject> list = new ArrayList<>();
        if (v != null && v.getMap() != null) {
            for (Map.Entry<String, LazyJsonValue> entry : v.getMap().entrySet()) {
                Long count = entry.getValue().getLong();
                if (count != null) {
                    list.add(new Subject(entry.getKey(), count));
                }
            }
        }
        return list;
    }

    /**
     * Construct a Subject instance
     * @param name the subject name
     * @param count the message count
     */
    public Subject(String name, long count) {
        this.name = name;
        this.count = count;
    }

    /**
     * Get the subject name
     * @return the subject
     */
    public String getName() {
        return name;
    }

    /**
     * Get the subject message count
     * @return the count
     */
    public long getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "Subject {\"name\":\"" + name + "\", \"count\":" + count + "}";
    }

    @Override
    public int compareTo(Subject o) {
        return name.compareTo(o.name);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof Subject)) return false;
        Subject that = (Subject) o;
        return count == that.count && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + Long.hashCode(count);
        return result;
    }
}
