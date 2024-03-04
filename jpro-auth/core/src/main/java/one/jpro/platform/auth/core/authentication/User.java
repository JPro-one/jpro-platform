package one.jpro.platform.auth.core.authentication;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of the {@link Authentication} interface to
 * be used on the client side to create authentication objects
 * from user data. The term client does not imply any particular
 * implementation characteristics (e.g., whether the application
 * executes on a server, a desktop, or other devices).
 *
 * @author Besmir Beqiri
 */
public class User implements Authentication {

    @NotNull
    private final String name;

    @NotNull
    private final Set<String> roles;

    @NotNull
    private final Map<String, Object> attributes;

    /**
     * Create a user holding a name.
     *
     * @param name the user's name
     */
    public User(@NotNull String name) {
        this(name, null, null);
    }

    /**
     * Create a user holding a name and roles.
     *
     * @param name  the user's name
     * @param roles the user's roles
     */
    public User(@NotNull String name, @Nullable Set<String> roles) {
        this(name, roles, null);
    }

    /**
     * Create a user holding a name and attributes.
     *
     * @param name       the user's name
     * @param attributes the user's attributes
     */
    public User(@NotNull String name, @Nullable Map<String, Object> attributes) {
        this(name, null, attributes);
    }

    /**
     * Create a user holding a name, roles and attributes.
     *
     * @param name       the user's name
     * @param roles      the user's roles
     * @param attributes the user's attributes
     */
    public User(@NotNull String name, @Nullable Set<String> roles, @Nullable Map<String, Object> attributes) {
        Objects.requireNonNull(name, "User's name is null.");

        this.name = name;
        this.roles = (roles == null || roles.isEmpty()) ? Collections.emptySet() : roles;
        this.attributes = attributes == null ? Collections.emptyMap() : attributes;
    }

    /**
     * Create a user from a JSON object.
     *
     * @param json the JSON object
     */
    public User(@NotNull JSONObject json) {
        String username = json.optString(KEY_NAME); // check with name key
        if (username == null || username.isBlank()) {
            username = json.optString("username"); // check with username key
            if (username == null || username.isBlank()) {
                throw new AuthenticationException("User's name is null.");
            }
        }

        name = username;

        if (json.has(KEY_ROLES)) {
            this.roles = json.getJSONArray(KEY_ROLES).toList().stream().map(Object::toString)
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            this.roles = Collections.emptySet();
        }

        if (json.has(KEY_ATTRIBUTES)) {
            this.attributes = json.getJSONObject(KEY_ATTRIBUTES).toMap();
        } else {
            this.attributes = Collections.emptyMap();
        }
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    @NotNull
    @Unmodifiable
    public Set<String> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    @Override
    @NotNull
    @Unmodifiable
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    public boolean hasAttribute(String key) {
        return hasKey(toJSON().getJSONObject(KEY_ATTRIBUTES), key);
    }

    private boolean hasKey(JSONObject json, String key) {
        boolean exists = json.has(key);
        if (!exists) {
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String nextKey = keys.next();
                if (json.get(nextKey) instanceof JSONObject) {
                    exists = hasKey(json.getJSONObject(nextKey), key);
                }
            }
        }
        return exists;
    }

    /**
     * Merges this user with another user, combining their roles and attributes.
     * The name of this user is retained.
     *
     * @param other the other user to merge with.
     * @return a new User instance combining the information of both users.
     */
    public User merge(User other) {
        // Check if the other user is null, return this user
        if (other == null) {
            return this;
        }

        // Merge roles
        Set<String> mergedRoles = new HashSet<>(this.roles);
        mergedRoles.addAll(other.getRoles());

        // Merge attributes
        Map<String, Object> mergedAttributes = new HashMap<>(this.attributes);
        other.getAttributes().forEach(mergedAttributes::putIfAbsent);

        // Create a new User with the combined roles and attributes
        return new User(this.name, mergedRoles, mergedAttributes);
    }
}
