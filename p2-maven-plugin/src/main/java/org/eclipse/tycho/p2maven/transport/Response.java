/*******************************************************************************
 * Copyright (c) 2022 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2maven.transport;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface Response<T> extends AutoCloseable {

	int statusCode() throws IOException;

	Map<String, List<String>> headers();

	@Override
	void close();

	T body() throws IOException;

	URI getURI();

	String getHeader(String header);

	long getLastModified();

	default void checkResponseCode() throws FileNotFoundException, IOException {
		int code = statusCode();
		if (code >= HttpURLConnection.HTTP_BAD_REQUEST) {
			if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_GONE) {
				throw new FileNotFoundException(getURI().toString());
			} else {
				throw new java.io.IOException("Server returned HTTP code: " + code + " for URL " + getURI().toString());
			}
		}
	}

}
