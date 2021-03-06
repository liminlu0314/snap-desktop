/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.raster.rcp.actions;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.ProductNode;
import org.esa.snap.core.datamodel.VirtualBand;
import org.esa.snap.engine_utilities.datamodel.Unit;
import org.esa.snap.rcp.actions.AbstractSnapAction;
import org.esa.snap.rcp.util.Dialogs;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;

import javax.swing.*;
import java.awt.event.ActionEvent;

@ActionID(category = "Raster", id = "org.esa.snap.raster.rcp.actions.ComplexToPhaseAction")
@ActionRegistration(displayName = "#CTL_ComplexToPhaseAction_Text")
@ActionReference(path = "Menu/Raster/Data Conversion", position = 200)
@NbBundle.Messages({
        "CTL_ComplexToPhaseAction_Text=Complex i and q to Phase",
        "CTL_ComplexToPhaseAction_Description=Creates a virtual phase band from i and q complex bands."
})
/**
 * ComplexToPhase action.
 */
public class ComplexToPhaseAction extends AbstractSnapAction implements ContextAwareAction, LookupListener {

    private final Lookup lkp;

    public ComplexToPhaseAction() {
        this(Utilities.actionsGlobalContext());
    }

    public ComplexToPhaseAction(Lookup lkp) {
        this.lkp = lkp;
        Lookup.Result<ProductNode> lkpContext = lkp.lookupResult(ProductNode.class);
        lkpContext.addLookupListener(WeakListeners.create(LookupListener.class, this, lkpContext));
        setEnableState();

        putValue(NAME, Bundle.CTL_ComplexToPhaseAction_Text());
        putValue(SHORT_DESCRIPTION, Bundle.CTL_ComplexToPhaseAction_Description());
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ComplexToPhaseAction(actionContext);
    }

    @Override
    public void resultChanged(LookupEvent ev) {
        setEnableState();
    }

    @Override
    public void actionPerformed(ActionEvent event) {

        final ProductNode productNode = lkp.lookup(ProductNode.class);
        if (productNode != null && productNode instanceof Band) {
            final Band band = (Band) productNode;
            final Product product = band.getProduct();
            String bandName = band.getName();
            final String unit = band.getUnit();
            String iBandName, qBandName, phaseBandName;

            if (unit != null && unit.contains(Unit.REAL)) {

                iBandName = bandName;
                qBandName = bandName.replaceFirst("i_", "q_");
                phaseBandName = bandName.replaceFirst("i_", "Phase_");
            } else if (unit != null && unit.contains(Unit.IMAGINARY)) {

                iBandName = bandName.replaceFirst("q_", "i_");
                qBandName = bandName;
                phaseBandName = bandName.replaceFirst("q_", "Phase_");
            } else {
                return;
            }

            if (product.getBand(iBandName) == null) {
                Dialogs.showWarning(product.getName() + " missing " + iBandName + " band");
                return;
            }
            if (product.getBand(qBandName) == null) {
                Dialogs.showWarning(product.getName() + " missing " + qBandName + " band");
                return;
            }

            if (product.getBand(phaseBandName) != null) {
                Dialogs.showWarning(product.getName() + " already contains a " + phaseBandName + " band");
                return;
            }

            if (Dialogs.requestDecision("Convert to Intensity", "Would you like to convert i and q bands " +
                    " to Phase in a new virtual band?", true, null) == Dialogs.Answer.YES) {
                convert(product, iBandName, qBandName, phaseBandName);
            }
        }
    }

    public void setEnableState() {
        final ProductNode productNode = lkp.lookup(ProductNode.class);
        if (productNode != null && productNode instanceof Band) {
            final Band band = (Band) productNode;
            final String unit = band.getUnit();
            if (unit != null && (unit.contains(Unit.REAL) || unit.contains(Unit.IMAGINARY))) {
                setEnabled(true);
                return;
            }
        }
        setEnabled(false);
    }

    public static void convert(final Product product,
                               final String iBandName, final String qBandName, final String phaseBandName) {

        final Band iBand = product.getBand(iBandName);
        final String expression = "atan2(" + qBandName + ',' + iBandName + ')';

        final VirtualBand virtBand = new VirtualBand(phaseBandName,
                                                     ProductData.TYPE_FLOAT32,
                                                     iBand.getRasterWidth(),
                                                     iBand.getRasterHeight(),
                                                     expression);
        virtBand.setUnit(Unit.PHASE);
        virtBand.setDescription("Phase from complex data");
        virtBand.setNoDataValueUsed(true);
        virtBand.setOwner(product);
        product.addBand(virtBand);
    }

}
