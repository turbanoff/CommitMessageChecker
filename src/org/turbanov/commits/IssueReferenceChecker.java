package org.turbanov.commits;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.util.ui.UIUtil;

/**
 * @author Andrey Turbanov
 * @since 12.02.2017
 */
class IssueReferenceChecker extends CheckinHandler {
    private static final String CHECKER_STATE_KEY = "COMMIT_MESSAGE_ISSUE_CHECKER_STATE_KEY";
    private static final Pattern ISSUE_REF_PATTERN = Pattern.compile("DXCORE-\\d{4,}");
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

        VcsRepositoryManager repoManager = VcsRepositoryManager.getInstance(panel.getProject());
        Collection<Repository> repositories = repoManager.getRepositories();
        Optional<Boolean> shouldCommit = repositories.stream()
                .map(Repository::getCurrentBranchName)
                .filter(Objects::nonNull)
                .map(ISSUE_REF_PATTERN::matcher)
                .filter(Matcher::find)
                .map(Matcher::group)
                .findFirst()
                .map(this::findReferenceInMessage);
        return shouldCommit.orElse(true) ? ReturnResult.COMMIT : ReturnResult.CANCEL;
    }

    private boolean findReferenceInMessage(String fromBranch) {
        String commitMessage = panel.getCommitMessage();
        Matcher matcher = ISSUE_REF_PATTERN.matcher(commitMessage);
        while (matcher.find()) {
            if (matcher.group().equals(fromBranch)) return true;
        }

        int yesNo = Messages.showYesNoDialog("Commit message doesn't contain reference to the issue " + fromBranch +
                ".\nAre you sure to commit as is?",
                "Missing Issue Reference",
                UIUtil.getWarningIcon());
        return yesNo == Messages.YES;
    }
}
