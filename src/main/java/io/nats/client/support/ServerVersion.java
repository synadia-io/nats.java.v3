package io.nats.client.support;

public class ServerVersion implements Comparable<ServerVersion> {
    final Integer major;
    final Integer minor;
    final Integer patch;
    final String extra;

    public ServerVersion(String v) {
        int mjr;
        int mnr = -1;
        int ptch = -1;
        String xtra = null;
        try {
            String[] split;
            if (v.startsWith("v")) {
                split = v.substring(1).replace("-", ".").split("\\Q.\\E");
            }
            else {
                split = v.replace("-", ".").split("\\Q.\\E");
            }
            mjr = Integer.parseInt(split[0]);
            mnr = Integer.parseInt(split[1]);
            ptch = split.length < 3 ? -1 : Integer.parseInt(split[2]);

            for (int i = 3; i < split.length; i++) {
                if (i == 3) {
                    xtra = "-" + split[i];
                }
                else {
                    //noinspection StringConcatenationInLoop
                    xtra = xtra + "." + split[i];
                }
            }
        }
        catch (NumberFormatException nfe) {
            mjr = -1;
        }
        if (mjr == -1) {
            major = -1;
            minor = -1;
            patch = -1;
            extra = null;
        }
        else {
            major = mjr;
            minor = mnr;
            patch = ptch;
            extra = xtra;
        }
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (extra == null ? "" : extra);
    }

    @Override
    public int compareTo(ServerVersion o) {
        int c = major.compareTo(o.major);
        if (c == 0) {
            c = minor.compareTo(o.minor);
            if (c == 0) {
                c = patch.compareTo(o.patch);
                if (c == 0) {
                    if (extra == null) {
                        c = o.extra == null ? 0 : 1;
                    }
                    else if (o.extra == null) {
                        c = -1;
                    }
                    else {
                        c = extra.compareTo(o.extra);
                    }
                }
            }
        }
        return c;
    }

    public static boolean isNewer(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) > 0;
    }

    public static boolean isSame(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) == 0;
    }

    public static boolean isOlder(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) < 0;
    }

    public static boolean isSameOrOlder(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) <= 0;
    }

    public static boolean isSameOrNewer(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) >= 0;
    }
}
