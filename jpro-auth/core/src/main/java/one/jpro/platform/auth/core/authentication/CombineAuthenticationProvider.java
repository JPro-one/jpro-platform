package one.jpro.platform.auth.core.authentication;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This class allows for the aggregation of multiple authentication providers,
 * where the authentication process can be tailored to succeed based on the
 * success of either all (AND logic) or any (OR logic) of the included providers.
 *
 * @author Besmir Beqiri
 */
public class CombineAuthenticationProvider implements AuthenticationProvider<Credentials> {

    /**
     * Creates a combined authentication provider that will resolve if all
     * contained authentication providers are successful. This is equivalent
     * to an AND operation among all providers.
     *
     * @return new instance of CombineAuthenticationProvider set to require all providers to succeed.
     */
    public static CombineAuthenticationProvider all() {
        return new CombineAuthenticationProvider(true);
    }

    /**
     * Creates a combined authentication provider that will resolve if any
     * contained authentication provider is successful. This is equivalent
     * to an OR operation among all providers.
     *
     * @return new instance of CombineAuthenticationProvider set to require any provider to succeed.
     */
    public static CombineAuthenticationProvider any() {
        return new CombineAuthenticationProvider(false);
    }

    private final List<AuthenticationProvider<? super Credentials>> providers = new ArrayList<>();
    private final boolean all;

    private CombineAuthenticationProvider(boolean all) {
        this.all = all;
    }

    /**
     * Adds an authentication provider to this combined authentication provider.
     * <p>
     * This method allows for the dynamic addition of authentication providers
     * into the combined provider. The added provider will participate in the
     * authentication process according to the logic (all/any) set for this
     * combined provider.
     * </p>
     *
     * @param other the authentication provider to add
     * @return self-reference for method chaining
     */
    public CombineAuthenticationProvider add(AuthenticationProvider<? super Credentials> other) {
        providers.add(other);
        return this;
    }

    @Override
    public CompletableFuture<User> authenticate(@NotNull Credentials credentials) {
        try {
            credentials.validate(null);
        } catch (CredentialValidationException ex) {
            return CompletableFuture.failedFuture(ex);
        }

        if (providers.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new AuthenticationException("The combined providers list is empty."));
        } else {
            return iterate(0, credentials, null);
        }
    }

    private CompletableFuture<User> iterate(final int idx, final Credentials credentials, final User previousUser) {
        // stop condition
        if (idx >= providers.size()) {
            if (!all) {
                // no more providers, means that we failed to find a provider capable of performing this operation
                return CompletableFuture.failedFuture(
                        new AuthenticationException("No provider capable of performing this operation."));
            } else {
                // if ALL then a success completes
                return CompletableFuture.completedFuture(previousUser);
            }
        }

        // attempt to perform operation
        return providers.get(idx)
                .authenticate(credentials)
                .thenCompose(user -> {
                    if (!all) {
                        // if ANY then a success completes
                        return CompletableFuture.completedFuture(user);
                    } else {
                        // if ALL then a success check the next one
                        return iterate(idx + 1, credentials, previousUser == null ? user : previousUser.merge(user));
                    }
                })
                .exceptionallyCompose(err -> {
                    // try again with next provider
                    if (!all) {
                        // try again with next provider
                        return iterate(idx + 1, credentials, null);
                    } else {
                        // short circuit when ALL is used a failure is enough to terminate
                        // no more providers, means that we failed to find a provider capable of performing this operation
                        return CompletableFuture.failedFuture(err);
                    }
                });
    }
}
