package io.synadia.client.testutils;

/**
 * Represents a parsed server version string in the form "major.minor.patch[-extra]".
 * Supports comparison for determining server capability.
 * Handles versions with or without a leading "v" prefix, and optional
 * pre-release suffixes separated by hyphens (e.g., "2.10.1-beta.1").
 * A version with a pre-release suffix is considered less than the same version without one,
 * consistent with semantic versioning.
 */
public class ServerVersion implements Comparable<ServerVersion> {
    private final int major;
    private final int minor;
    private final int patch;
    private final String extra;

    /**
     * Parse a version string into its components.
     * Accepts formats like "2.10.1", "v2.10.1", "2.10.1-beta.1".
     * If the string cannot be parsed, all components are set to -1.
     * @param v the version string to parse
     */
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

    /**
     * Returns the version as a string in the form "major.minor.patch[-extra]".
     * @return the version string
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (extra == null ? "" : extra);
    }

    /**
     * Compares this version to another. Compares major, minor, patch in order,
     * then extra (pre-release suffix). A version without extra is considered
     * greater than the same version with extra (semver semantics).
     * @param o the other version to compare to
     * @return negative if this is older, zero if same, positive if this is newer
     */
    @Override
    public int compareTo(ServerVersion o) {
        int c = Integer.compare(major, o.major);
        if (c == 0) {
            c = Integer.compare(minor, o.minor);
            if (c == 0) {
                c = Integer.compare(patch, o.patch);
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

    /**
     * Determine if version v is newer than version than.
     * @param v the version to check
     * @param than the version to compare against
     * @return true if v is strictly newer than
     */
    public static boolean isNewer(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) > 0;
    }

    /**
     * Determine if version v is the same as version than.
     * @param v the version to check
     * @param than the version to compare against
     * @return true if v is the same as than
     */
    public static boolean isSame(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) == 0;
    }

    /**
     * Determine if version v is older than version than.
     * @param v the version to check
     * @param than the version to compare against
     * @return true if v is strictly older than
     */
    public static boolean isOlder(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) < 0;
    }

    /**
     * Determine if version v is the same or older than version than.
     * @param v the version to check
     * @param than the version to compare against
     * @return true if v is the same or older than than
     */
    public static boolean isSameOrOlder(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) <= 0;
    }

    /**
     * Determine if version v is the same or newer than version than.
     * @param v the version to check
     * @param than the version to compare against
     * @return true if v is the same or newer than than
     */
    public static boolean isSameOrNewer(String v, String than) {
        return new ServerVersion(v).compareTo(new ServerVersion(than)) >= 0;
    }
}
