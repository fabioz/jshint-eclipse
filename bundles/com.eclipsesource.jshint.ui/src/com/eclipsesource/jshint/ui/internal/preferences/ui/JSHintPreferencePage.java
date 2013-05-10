/*******************************************************************************
 * Copyright (c) 2012 EclipseSource.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Ralf Sternberg - initial implementation and API
 ******************************************************************************/
package com.eclipsesource.jshint.ui.internal.preferences.ui;

import java.io.File;
import java.io.FileInputStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.eclipsesource.jshint.JSHint;
import com.eclipsesource.jshint.ui.internal.Activator;
import com.eclipsesource.jshint.ui.internal.preferences.JSHintPreferences;

public class JSHintPreferencePage extends PreferencePage implements
		IWorkbenchPreferencePage {

	private final JSHintPreferences preferences;
	private Button defaultLibButton;
	private Button customLibButton;
	private Text customLibPathText;
	private Button customLibPathButton;

	public JSHintPreferencePage() {
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("General settings for JSHint");
		preferences = new JSHintPreferences();
	}

	public void init(IWorkbench workbench) {
	}

	@Override
	protected IPreferenceStore doGetPreferenceStore() {
		return null;
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout mainLayout = createMainLayout();
		mainLayout.marginTop = 10;
		composite.setLayout(mainLayout);
		createCustomJSHintArea(composite);
		updateControls();
		return composite;
	}

	@Override
	public boolean performOk() {
		try {
			if (preferences.hasChanged()) {
				preferences.save();
				// triggerRebuild();
			}
		} catch (CoreException exception) {
			Activator.logError("Failed to save preferences", exception);
			return false;
		}
		return true;
	}

	@Override
	protected void performDefaults() {
		preferences.resetToDefaults();
		updateControls();
		super.performDefaults();
	}

	private void createCustomJSHintArea(Composite parent) {
		defaultLibButton = new Button(parent, SWT.RADIO);
		defaultLibButton.setText("Use the &built-in JSHint library (version "
				+ JSHint.getDefaultLibraryVersion() + ")");
		defaultLibButton.setLayoutData(createFillData(3));
		customLibButton = new Button(parent, SWT.RADIO);
		customLibButton
				.setText("Provide a &custom JSHint library file (JSLint is also supported)");
		customLibButton.setLayoutData(createFillData(3));
		customLibButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				updateValuesFromControls();
				updateControls();
			}
		});
		customLibPathText = new Text(parent, SWT.BORDER);
		GridData textData = createFillData(2);
		textData.horizontalIndent = 25;
		customLibPathText.setLayoutData(textData);
		customLibPathText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateValuesFromControls();
			}
		});
		customLibPathButton = new Button(parent, SWT.PUSH);
		customLibPathButton.setText("Select");
		customLibPathButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectFile();
			}
		});
		Text customLibPathLabelText = new Text(parent, SWT.READ_ONLY | SWT.WRAP);
		customLibPathLabelText
				.setText("This file is usually named 'jshint.js' or 'jslint.js'.");
		customLibPathLabelText.setBackground(parent.getBackground());
		GridData labelTextData = createFillData(2);
		labelTextData.horizontalIndent = 25;
		customLibPathLabelText.setLayoutData(labelTextData);
	}

	private void selectFile() {
		FileDialog fileDialog = new FileDialog(getShell(), SWT.OPEN);
		fileDialog.setText("Select JSHint library file");
		File file = new File(preferences.getCustomLibPath());
		fileDialog.setFileName(file.getName());
		fileDialog.setFilterPath(file.getParent());
		fileDialog.setFilterNames(new String[] { "JavaScript files" });
		fileDialog.setFilterExtensions(new String[] { "*.js", "" });
		String selectedPath = fileDialog.open();
		if (selectedPath != null) {
			preferences.setCustomLibPath(selectedPath);
			updateControls();
		}
	}

	private void updateValuesFromControls() {
		preferences.setUseCustomLib(customLibButton.getSelection());
		preferences.setCustomLibPath(customLibPathText.getText());
		validate();
	}

	private void validate() {
		setErrorMessage(null);
		setValid(false);
		final Display display = getShell().getDisplay();
		Job validator = new Job("JSHint preferences validation") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					monitor.beginTask("checking preferences", 1);
					validatePrefs();
					display.asyncExec(new Runnable() {
						public void run() {
							setValid(true);
						}
					});
				} catch (final IllegalArgumentException exception) {
					display.asyncExec(new Runnable() {
						public void run() {
							setErrorMessage(exception.getMessage());
						}
					});
				} finally {
					monitor.done();
				}
				return Status.OK_STATUS;
			}
		};
		validator.schedule();
	}

	private void validatePrefs() {
		if (preferences.getUseCustomLib()) {
			String path = preferences.getCustomLibPath();
			File file = new File(path);
			validateFile(file);
		}
	}

	private static void validateFile(File file) throws IllegalArgumentException {
		if (!file.isFile()) {
			throw new IllegalArgumentException("File does not exist");
		}
		if (!file.canRead()) {
			throw new IllegalArgumentException("File is not readable");
		}
		try {
			FileInputStream inputStream = new FileInputStream(file);
			try {
				JSHint jsHint = new JSHint();
				jsHint.load(inputStream);
			} finally {
				inputStream.close();
			}
		} catch (Exception exception) {
			throw new IllegalArgumentException(
					"File is not a valid JSHint library");
		}
	}

	private void updateControls() {
		boolean useCustomLib = preferences.getUseCustomLib();
		defaultLibButton.setSelection(!useCustomLib);
		customLibButton.setSelection(useCustomLib);
		customLibPathText.setText(preferences.getCustomLibPath());
		customLibPathText.setEnabled(useCustomLib);
		customLibPathButton.setEnabled(useCustomLib);
	}

	// private void triggerRebuild() throws CoreException {
	// IProject[] projects =
	// ResourcesPlugin.getWorkspace().getRoot().getProjects();
	// for( IProject project : projects ) {
	// if( project.isAccessible() ) {
	// BuilderUtil.triggerClean( project, JSHintBuilder.ID );
	// }
	// }
	// }

	private static GridLayout createMainLayout() {
		GridLayout layout = new GridLayout(3, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		return layout;
	}

	private static GridData createFillData(int span) {
		GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		data.horizontalSpan = span;
		return data;
	}

}
