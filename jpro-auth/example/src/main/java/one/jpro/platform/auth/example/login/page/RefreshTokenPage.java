package one.jpro.platform.auth.example.login.page;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import one.jpro.platform.auth.example.login.LoginApp;
import one.jpro.platform.mdfx.MarkdownView;

/**
 * Refresh token page.
 *
 * @author Besmir Beqiri
 */
public class RefreshTokenPage extends Page {

    public RefreshTokenPage(LoginApp loginApp) {
        final var headerLabel = new Label("Authentication information\n" +
                "after refreshing the access token:");
        headerLabel.getStyleClass().add("header-label");

        MarkdownView markdownView = new MarkdownView();
        markdownView.getStylesheets().add("/one/jpro/mdfx/mdfx-default.css");
        markdownView.mdStringProperty().bind(Bindings.createStringBinding(() -> {
            final var user = loginApp.getUser();
            return user == null ? "" : loginApp.jsonToMarkdown(user.toJSON());
        }, loginApp.userProperty()));

        final var pane = new VBox(headerLabel, markdownView);
        pane.getStyleClass().add("auth-info-pane");

        getChildren().add(pane);
    }
}
