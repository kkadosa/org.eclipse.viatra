/*******************************************************************************
 * Copyright (c) 2010-2013, Abel Hegedus, Istvan Rath and Daniel Varro
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-v20.html.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.viatra.query.runtime.internal.engine;

import static org.eclipse.viatra.query.runtime.matchers.util.Preconditions.checkArgument;

import java.util.HashSet;
import java.util.Set;

public abstract class ListenerContainer<Listener> {
    
    protected final Set<Listener> listeners;
    
    public ListenerContainer() {
        this.listeners = new HashSet<Listener>();
    }
    
    public synchronized void addListener(Listener listener) {
        checkArgument(listener != null, "Cannot add null listener!");
        boolean added = listeners.add(listener);
        if(added) {
            listenerAdded(listener);
        }
    }
    
    public synchronized void removeListener(Listener listener) {
        checkArgument(listener != null, "Cannot remove null listener!");
        boolean removed = listeners.remove(listener);
        if(removed) {
            listenerRemoved(listener);
        }
    }
    
    protected abstract void listenerAdded(Listener listener);
    
    protected abstract void listenerRemoved(Listener listener);
}