package org.turbanov.commits;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import static com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer.formatTextWithLinks;

/**
 * @author Andrey Turbanov
 * @since 12.02.2017
 */
class IssueReferenceChecker extends CheckinHandler {
    private static final String CHECKER_STATE_KEY = "COMMIT_MESSAGE_ISSUE_CHECKER_STATE_KEY";
    private final CheckinProjectPanel panel;

    public IssueReferenceChecker(CheckinProjectPanel panel) {
        this.panel = panel;
    }

    @Override
    public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
        final JCheckBox checkBox = new JCheckBox("Check reference to issue in message");

        return new RefreshableOnComponent() {
            @Override
            public JComponent getComponent() {
                JPanel root = new JPanel(new BorderLayout());
                root.add(checkBox, "West");
                return root;
            }

            @Override
            public void refresh() {
            }

            @Override
            public void saveState() {
                PropertiesComponent.getInstance().setValue(CHECKER_STATE_KEY, checkBox.isSelected());
            }

            @Override
            public void restoreState() {
                checkBox.setSelected(isCheckMessageEnabled());
            }
        };
    }

    public static boolean isCheckMessageEnabled() {
        return PropertiesComponent.getInstance().getBoolean(CHECKER_STATE_KEY, true);
    }

    @Override
    public ReturnResult beforeCheckin() {
        if (!isCheckMessageEnabled()) return super.beforeCheckin();

        Project project = panel.getProject();
        IssueNavigationConfiguration configuration = IssueNavigationConfiguration.getInstance(project);
        VcsRepositoryManager repoManager = VcsRepositoryManager.getInstance(project);
        Optional<Boolean> shouldCommit = repoManager.getRepositories().stream()
                .map(Repository::getCurrentBranchName)
                .filter(Objects::nonNull)
                .flatMap(branchName -> match(configuration, branchName))
                .filter(trinity -> trinity.first.find())
                .map(trinity -> Trinity.create(trinity.first.group(), trinity.second, trinity.third))
                .findFirst()
                .map(branchMatch -> findReferenceInMessage(branchMatch, project));
        return shouldCommit.orElse(true) ? ReturnResult.COMMIT : ReturnResult.CANCEL;
    }

    private Stream<Trinity<Matcher, Pattern, String>> match(IssueNavigationConfiguration configuration, String branch) {
        return configuration.getLinks().stream()
                .map(link -> {
                    Pattern pattern = link.getIssuePattern();
                    Matcher matcher = pattern.matcher(branch);
                    return Trinity.create(matcher, pattern, branch);
                });
    }

    private boolean findReferenceInMessage(Trinity<String, Pattern, String> branchMatch, Project project) {
        String commitMessage = panel.getCommitMessage();
        Pattern pattern = branchMatch.second;
        Matcher matcher = pattern.matcher(commitMessage);
        String issueReferenceFromBranchName = branchMatch.first;
        while (matcher.find()) {
            if (matcher.group().equals(issueReferenceFromBranchName)) return true;
        }

        String branchName = branchMatch.third;
        String html = "<html><body>" +
                "Commit message doesn't contain reference to the issue " + formatTextWithLinks(project, issueReferenceFromBranchName) + "<br>" +
                "Current branch name: <code>" + XmlStringUtil.escapeString(branchName) + "</code><br>" +
                "<br>" +
                "Are you sure to commit as is?" +
                "</body></html>";
        int yesNo = Messages.showYesNoDialog(html,
                "Missing Issue Reference",
                UIUtil.getWarningIcon());
        return yesNo == Messages.YES;
    }
}
