package com.intellij.refactoring.migration;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMigration;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;

import java.util.ArrayList;

/**
 * @author ven
 */
class MigrationProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.migration.MigrationProcessor");
  private final MigrationMap myMigrationMap;

  public MigrationProcessor(Project project, MigrationMap migrationMap) {
    super(project);
    myMigrationMap = migrationMap;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new MigrationUsagesViewDescriptor(myMigrationMap, false, usages);
  }

  private PsiMigration startMigration(final PsiManager psiManager) {
    final PsiMigration migration = psiManager.startMigration();

    final Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      LOG.assertTrue(progressIndicator != null);
      application.invokeAndWait(
          new Runnable() {
          public void run() {
            findOrCreateEntries(psiManager, migration, application);
          }
        }, progressIndicator.getModalityState());
    } else {
      findOrCreateEntries(psiManager, migration, application);
    }

    return migration;
  }

  private void findOrCreateEntries(final PsiManager psiManager, final PsiMigration migration, Application application) {
    application.runWriteAction(
        new Runnable() {
        public void run() {
          for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
            MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
            if (entry.getType() == MigrationMapEntry.PACKAGE) {
              MigrationUtil.findOrCreatePackage(psiManager, migration,
                                                entry.getOldName());
            }
            else {
              MigrationUtil.findOrCreateClass(psiManager, migration, entry.getOldName());
            }
          }
        }
      });
  }

  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> usagesVector = new ArrayList<UsageInfo>();
    PsiManager psiManager = PsiManager.getInstance(myProject);
    PsiMigration psiMigration = startMigration(psiManager);
    try {
      if (myMigrationMap == null) {
        return null;
      }
      for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
        UsageInfo[] usages;
        if (entry.getType() == MigrationMapEntry.PACKAGE) {
          usages = MigrationUtil.findPackageUsages(psiManager, psiMigration, entry.getOldName());
        }
        else {
          usages = MigrationUtil.findClassUsages(psiManager, psiMigration, entry.getOldName());
        }

        for (int j = 0; j < usages.length; j++) {
          usagesVector.add(new MigrationUsageInfo(usages[j], entry));
        }
      }
    }
    finally {
      psiMigration.finish();
    }
    return usagesVector.toArray(new MigrationUsageInfo[usagesVector.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    if (usages[0].length == 0) {
      Messages.showInfoMessage(myProject, "No Usages Found in the Project", "Migration");
      return false;
    }
    setPreviewUsages(true);
    return true;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    final PsiMigration psiMigration = psiManager.startMigration();
    LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, getCommandName());

    try {
      for (int i = 0; i < myMigrationMap.getEntryCount(); i++) {
        MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
        if (entry.getType() == MigrationMapEntry.PACKAGE) {
          MigrationUtil.doPackageMigration(psiManager, psiMigration, entry.getNewName(), usages);
        }
        if (entry.getType() == MigrationMapEntry.CLASS) {
          MigrationUtil.doClassMigration(psiManager, psiMigration, entry.getNewName(), usages);
        }
      }
    }
    finally {
      LvcsIntegration.checkinFilesAfterRefactoring(myProject, action);
      psiMigration.finish();
    }
  }


  protected String getCommandName() {
    return "Migration";
  }

  public static class MigrationUsageInfo extends UsageInfo {
    public MigrationMapEntry mapEntry;

    public MigrationUsageInfo(UsageInfo info, MigrationMapEntry mapEntry) {
      super(info.getElement(), info.startOffset, info.endOffset);
      this.mapEntry = mapEntry;
    }
  }
}