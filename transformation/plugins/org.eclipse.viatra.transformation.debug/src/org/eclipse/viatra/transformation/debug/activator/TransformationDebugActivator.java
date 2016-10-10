/**
 * Copyright (c) 2010-2016, Peter Lunk, IncQuery Labs Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Peter Lunk - initial API and implementation
 */
package org.eclipse.viatra.transformation.debug.activator;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.ui.actions.ExportBreakpointsOperation;
import org.eclipse.debug.ui.actions.ImportBreakpointsOperation;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.viatra.query.runtime.util.ViatraQueryLoggingUtil;
import org.eclipse.viatra.transformation.debug.util.BreakpointCacheUtil;
import org.osgi.framework.BundleContext;

/**
 * Activator class for the VIATRA transformation debugger plug-in.
 * 
 * @author Peter Lunk
 */
public class TransformationDebugActivator extends AbstractUIPlugin {
    public static final String PLUGIN_ID = "org.eclipse.viatra.transformation.debug"; //$NON-NLS-1$

    private static TransformationDebugActivator plugin;

    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        
        
        if(BreakpointCacheUtil.breakpointCacheExists()){
            ImportBreakpointsOperation operation = new ImportBreakpointsOperation(
                    BreakpointCacheUtil.getBreakpointCacheLocation().trim(), 
                    false, 
                    false);
            try {
                operation.run(null);
            } catch (InvocationTargetException e) {
                ViatraQueryLoggingUtil.getDefaultLogger().error(e.getMessage(), e);
            }
        }
    }

    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
        
        ExportBreakpointsOperation operation = new ExportBreakpointsOperation(
                BreakpointCacheUtil.filterBreakpoints(DebugPlugin.getDefault().getBreakpointManager().getBreakpoints()),
                BreakpointCacheUtil.getBreakpointCacheLocation());
        operation.run(new NullProgressMonitor());
        
        
    }

    public static TransformationDebugActivator getDefault() {
        return plugin;
    }

}
