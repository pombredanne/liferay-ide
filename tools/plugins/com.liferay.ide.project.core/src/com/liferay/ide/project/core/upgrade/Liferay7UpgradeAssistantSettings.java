/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.ide.project.core.upgrade;

/**
 * @author Lovett Li
 */
public class Liferay7UpgradeAssistantSettings {

	public Liferay7UpgradeAssistantSettings() {
	}

	public String[] getJavaProjectLocations() {
		return _javaProjectLocations;
	}

	public PortalSettings getPortalSettings() {
		return _portalSettings;
	}

	public void setJavaProjectLocations(String[] locations) {
		_javaProjectLocations = locations;
	}

	public void setPortalSettings(PortalSettings portalSettings) {
		_portalSettings = portalSettings;
	}

	private String[] _javaProjectLocations;
	private PortalSettings _portalSettings;

}