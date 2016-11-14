/*******************************************************************************
 * Copyright (c) 2015-2016, IncQuery Labs Ltd. and Ericsson AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Abel Hegedus, Daniel Segesdi, Robert Doczi, Zoltan Ujhelyi - initial API and implementation
 *******************************************************************************/
package org.eclipse.viatra.integration.evm.jdt

import org.eclipse.viatra.transformation.evm.api.event.Event
import org.eclipse.viatra.transformation.evm.api.event.EventType

class JDTEvent implements Event<JDTEventAtom> {
	EventType type
	JDTEventAtom atom

	/** 
	 * @param type
	 * @param atom
	 */
	new(EventType type, JDTEventAtom atom) {
		this.type = type
		this.atom = atom
	}


	override EventType getEventType() {
		return type
	}

	override JDTEventAtom getEventAtom() {
		return atom
	}

}
