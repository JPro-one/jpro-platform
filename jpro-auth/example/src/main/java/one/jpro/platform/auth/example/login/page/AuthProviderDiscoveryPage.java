package one.jpro.platform.auth.example.login.page;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import one.jpro.platform.auth.example.login.LoginApp;
import one.jpro.platform.auth.oauth2.OAuth2AuthenticationProvider;
import one.jpro.platform.mdfx.MarkdownView;

/**
 * Authorization provider discovery page.
 *
 * @author Besmir Beqiri
 */
public class AuthProviderDiscoveryPage extends Page {

    public AuthProviderDiscoveryPage(LoginApp loginApp,
                                     OAuth2AuthenticationProvider authProvider) {
        final var headerLabel = new Label("OpenID Connect Discovery: " + loginApp.getAuthProviderName(authProvider));
        headerLabel.getStyleClass().add("header-label");

        MarkdownView providerDiscoveryView = new MarkdownView();
        providerDiscoveryView.getStylesheets().add("/one/jpro/mdfx/mdfx-default.css");
        providerDiscoveryView.mdStringProperty().bind(Bindings.createStringBinding(() -> {
            final var authOptions = loginApp.getAuthOptions();
            return authOptions == null ? "" : loginApp.jsonToMarkdown(authOptions.toJSON());
        }, loginApp.authOptionsProperty()));

        final var pane = new VBox(headerLabel, providerDiscoveryView);
        pane.getStyleClass().add("openid-provider-discovery-pane");

        getChildren().add(pane);
    }
}
