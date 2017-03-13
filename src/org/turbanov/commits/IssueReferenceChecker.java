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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.util.ui.UIUtil;

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
                .flatMap(s -> match(configuration, s))
                .filter(pair -> pair.first.find())
                .map(pair -> Pair.create(pair.first.group(), pair.second))
                .findFirst()
                .map(branchMatch -> findReferenceInMessage(branchMatch, project));
        return shouldCommit.orElse(true) ? ReturnResult.COMMIT : ReturnResult.CANCEL;
    }

    private Stream<Pair<Matcher, Pattern>> match(IssueNavigationConfiguration configuration, String s) {
        return configuration.getLinks().stream()
                .map(link -> {
                    Pattern pattern = link.getIssuePattern();
                    Matcher matcher = pattern.matcher(s);
                    return Pair.create(matcher, pattern);
                });
    }

    private boolean findReferenceInMessage(Pair<String, Pattern> branchMatch, Project project) {
        String commitMessage = panel.getCommitMessage();
        Pattern pattern = branchMatch.second;
        Matcher matcher = pattern.matcher(commitMessage);
        String fromBranch = branchMatch.first;
        while (matcher.find()) {
            if (matcher.group().equals(fromBranch)) return true;
        }

        String message = "Commit message doesn't contain reference to the issue " + fromBranch +
                ".\nAre you sure to commit as is?";
        String html = IssueLinkHtmlRenderer.formatTextIntoHtml(project, message);
        int yesNo = Messages.showYesNoDialog(html,
                "Missing Issue Reference",
                UIUtil.getWarningIcon());
        return yesNo == Messages.YES;
    }
}