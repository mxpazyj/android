// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.exportSignedPackage;

import com.android.annotations.VisibleForTesting;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.compiler.artifact.ApkSigningSettingsForm;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUiUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import static com.intellij.credentialStore.CredentialAttributesKt.CredentialAttributes;

/**
 * @author Eugene.Kudelevsky
 */
class KeystoreStep extends ExportSignedPackageWizardStep implements ApkSigningSettingsForm {
  @VisibleForTesting static final String KEY_STORE_PASSWORD_KEY = "KEY_STORE_PASSWORD";
  @VisibleForTesting static final String KEY_PASSWORD_KEY = "KEY_PASSWORD";

  private static class KeyStorePasswordRequestor {
    // dummy: used as a requestor class id to access the key store password
  }

  private static class KeyPasswordRequestor {
    // dummy: used as a requestor class id to access the key password
  }

  private JPanel myContentPanel;
  private JPasswordField myKeyStorePasswordField;
  private JPasswordField myKeyPasswordField;
  private TextFieldWithBrowseButton.NoPathCompletion myKeyAliasField;
  private JTextField myKeyStorePathField;
  private JButton myCreateKeyStoreButton;
  private JButton myLoadKeyStoreButton;
  private JBCheckBox myRememberPasswordCheckBox;

  private final ExportSignedPackageWizard myWizard;
  private final boolean myUseGradleForSigning;

  KeystoreStep(ExportSignedPackageWizard wizard, boolean useGradleForSigning) {
    myWizard = wizard;
    myUseGradleForSigning = useGradleForSigning;
    final Project project = wizard.getProject();

    final GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(project);
    myKeyStorePathField.setText(settings.KEY_STORE_PATH);
    myKeyAliasField.setText(settings.KEY_ALIAS);
    myRememberPasswordCheckBox.setSelected(settings.REMEMBER_PASSWORDS);

    if (settings.REMEMBER_PASSWORDS) {
      final String keyStorePasswordKey = makePasswordKey(KEY_STORE_PASSWORD_KEY, settings.KEY_STORE_PATH, null);
      String password = retrievePassword(KeyStorePasswordRequestor.class, keyStorePasswordKey);
      if (password != null) {
        myKeyStorePasswordField.setText(password);
      }

      final String keyPasswordKey = makePasswordKey(KEY_PASSWORD_KEY, settings.KEY_STORE_PATH, settings.KEY_ALIAS);
      password = retrievePassword(KeyPasswordRequestor.class, keyPasswordKey);
      if (password != null) {
        myKeyPasswordField.setText(password);
      }
    }
    AndroidUiUtil.initSigningSettingsForm(project, this);
  }

  private static String retrievePassword(@NotNull Class<?> primaryRequestor, @NotNull String key) {
    final PasswordSafe passwordSafe = PasswordSafe.getInstance();
    String password = passwordSafe.getPassword(CredentialAttributesKt.CredentialAttributes(primaryRequestor, key));
    if (password == null) {
      // Try to retrieve password previously saved with an old requestor in order to make user experience more seamless
      // while transitioning to a version which contains the fix for b/64995008, rather than having them retype all the
      // passwords at once.
      password = passwordSafe.getPassword(CredentialAttributesKt.CredentialAttributes(KeystoreStep.class, key));
    }

    return password;
  }

  private static void updateSavedPassword(@NotNull Class<?> primaryRequestor, @NotNull String key, @Nullable String value) {
    final PasswordSafe passwordSafe = PasswordSafe.getInstance();
    passwordSafe.set(CredentialAttributes(primaryRequestor, key), value == null ? null : new Credentials(key, value));
    // Always erase the one stored with the old requestor (the one used before the fix for b/64995008).
    passwordSafe.set(CredentialAttributes(KeystoreStep.class, key), null);
  }

  @VisibleForTesting
  static String makePasswordKey(@NotNull String prefix, @NotNull String keyStorePath, @Nullable String keyAlias) {
    return prefix + "__" + keyStorePath + (keyAlias != null ? "__" + keyAlias : "");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myKeyStorePathField.getText().isEmpty()) {
      return myKeyStorePathField;
    }
    else if (myKeyStorePasswordField.getPassword().length == 0) {
      return myKeyStorePasswordField;
    }
    else if (myKeyAliasField.getText().isEmpty()) {
      return myKeyAliasField;
    }
    else if (myKeyPasswordField.getPassword().length == 0) {
      return myKeyPasswordField;
    }
    return null;
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @Override
  public String getHelpId() {
    return "reference.android.reference.extract.signed.package.specify.keystore";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    final String keyStoreLocation = myKeyStorePathField.getText().trim();
    if (keyStoreLocation.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.keystore.location.error"));
    }

    final char[] keyStorePassword = myKeyStorePasswordField.getPassword();
    if (keyStorePassword.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.store.password.error"));
    }

    final String keyAlias = myKeyAliasField.getText().trim();
    if (keyAlias.isEmpty()) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.alias.error"));
    }

    final char[] keyPassword = myKeyPasswordField.getPassword();
    if (keyPassword.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.key.password.error"));
    }

    if (myUseGradleForSigning) {
      myWizard.setGradleSigningInfo(new GradleSigningInfo(keyStoreLocation, keyStorePassword, keyAlias, keyPassword));
    } else {
      final KeyStore keyStore = loadKeyStore(new File(keyStoreLocation));
      if (keyStore == null) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.keystore.error.title"));
      }
      loadKeyAndSaveToWizard(keyStore, keyAlias, keyPassword);
    }

    final Project project = myWizard.getProject();
    final GenerateSignedApkSettings settings = GenerateSignedApkSettings.getInstance(project);

    settings.KEY_STORE_PATH = keyStoreLocation;
    settings.KEY_ALIAS = keyAlias;

    final boolean rememberPasswords = myRememberPasswordCheckBox.isSelected();
    settings.REMEMBER_PASSWORDS = rememberPasswords;

    final String keyStorePasswordKey = makePasswordKey(KEY_STORE_PASSWORD_KEY, keyStoreLocation, null);
    final String keyPasswordKey = makePasswordKey(KEY_PASSWORD_KEY, keyStoreLocation, keyAlias);

    updateSavedPassword(KeyStorePasswordRequestor.class, keyStorePasswordKey, rememberPasswords ? new String(keyStorePassword) : null);
    updateSavedPassword(KeyPasswordRequestor.class, keyPasswordKey, rememberPasswords ? new String(keyPassword) : null);
  }

  private KeyStore loadKeyStore(File keystoreFile) throws CommitStepException {
    final char[] password = myKeyStorePasswordField.getPassword();
    FileInputStream fis = null;
    AndroidUtils.checkPassword(password);
    if (!keystoreFile.isFile()) {
      throw new CommitStepException(AndroidBundle.message("android.cannot.find.file.error", keystoreFile.getPath()));
    }
    final KeyStore keyStore;
    try {
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      //noinspection IOResourceOpenedButNotSafelyClosed
      fis = new FileInputStream(keystoreFile);
      keyStore.load(fis, password);
    }
    catch (Exception e) {
      throw new CommitStepException(e.getMessage());
    }
    finally {
      if (fis != null) {
        try {
          fis.close();
        }
        catch (IOException ignored) {
        }
      }
      Arrays.fill(password, '\0');
    }
    return keyStore;
  }

  private void loadKeyAndSaveToWizard(KeyStore keyStore, String alias, char[] keyPassword) throws CommitStepException {
    KeyStore.PrivateKeyEntry entry;
    try {
      assert keyStore != null;
      entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(alias, new KeyStore.PasswordProtection(keyPassword));
    }
    catch (Exception e) {
      throw new CommitStepException("Error: " + e.getMessage());
    }
    if (entry == null) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", alias));
    }
    PrivateKey privateKey = entry.getPrivateKey();
    Certificate certificate = entry.getCertificate();
    if (privateKey == null || certificate == null) {
      throw new CommitStepException(AndroidBundle.message("android.extract.package.cannot.find.key.error", alias));
    }
    myWizard.setPrivateKey(privateKey);
    myWizard.setCertificate((X509Certificate)certificate);
  }

  @Override
  public JButton getLoadKeyStoreButton() {
    return myLoadKeyStoreButton;
  }

  @Override
  public JTextField getKeyStorePathField() {
    return myKeyStorePathField;
  }

  @Override
  public JPanel getPanel() {
    return myContentPanel;
  }

  @Override
  public JButton getCreateKeyStoreButton() {
    return myCreateKeyStoreButton;
  }

  @Override
  public JPasswordField getKeyStorePasswordField() {
    return myKeyStorePasswordField;
  }

  @Override
  public TextFieldWithBrowseButton getKeyAliasField() {
    return myKeyAliasField;
  }

  @Override
  public JPasswordField getKeyPasswordField() {
    return myKeyPasswordField;
  }
}
