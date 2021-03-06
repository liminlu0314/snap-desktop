/*
 * Copyright (C) 2014 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.ui.product;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.core.Assert;
import com.bc.ceres.swing.binding.ComponentAdapter;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductFilter;
import org.esa.snap.core.datamodel.ProductNode;
import org.esa.snap.core.util.Debug;
import org.esa.snap.ui.AppContext;
import org.esa.snap.ui.UIUtils;
import org.esa.snap.ui.tool.ToolButtonFactory;

import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;

/**
 * Enables clients to create a component for choosing source products. The list of source products can arbitrarily be
 * composed of
 * <ul>
 * <li>currently opened products</li>
 * <li>single products anywhere in the file system</li>
 * <li>whole directories anywhere in the file system and</li>
 * <li>recursive directories anywhere in the file system</li>
 * </ul>
 * <p>
 * The file paths the user chooses are stored as objects of type {@link java.io.File} within the property that is passed
 * into the constructor. Products that are chosen from the product tree can be retrieved via
 * {@link #getSourceProducts()}. So, clients of these must take care that the value in the given property is taken into
 * account as well as the return value of that method.
 *
 * The property that serves as target container for the source product paths must be of type
 * {@code String[].class}. Changes in the list are synchronised with the property. If the changes of the property
 * values outside this component shall be synchronised with the list, it is necessary that the property lies within a
 * property container.
 *
 * @author thomas
 */
public class SourceProductList extends ComponentAdapter {

    private final AppContext appContext;
    private final InputListModel listModel;
    private final JList inputPathsList;

    private String propertyNameLastOpenInputDir;
    private String propertyNameLastOpenedFormat;
    private boolean xAxis;
    private JComponent[] components;
    private ProductFilter productFilter;
    private String defaultPattern;

    /**
     * Constructor.
     *
     * @param appContext The context of the app using this component.
     *
     */
    public SourceProductList(AppContext appContext) {
        this.appContext = appContext;
        this.listModel = new InputListModel();
        this.inputPathsList = createInputPathsList(listModel);
        this.propertyNameLastOpenInputDir = "org.esa.snap.core.ui.product.lastOpenInputDir";
        this.propertyNameLastOpenedFormat = "org.esa.snap.core.ui.product.lastOpenedFormat";
        this.xAxis = true;
        this.defaultPattern = null;
        productFilter = product -> true;
    }

    /**
     * Creates an array of two JPanels. The first panel contains a list displaying the chosen products. The second panel
     * contains buttons for adding and removing products, laid out in vertical direction. Note that it makes only sense
     * to use both components.
     *
     * @return an array of two JPanels.
     */
    @Override
    public JComponent[] getComponents() {
        if (components == null) {
            components = createComponents();
        }
        return components;
    }

    /**
     * Creates an array of two JPanels. The first panel contains a list displaying the chosen products. The second panel
     * contains buttons for adding and removing products, laid out in configurable direction. Note that it makes only sense
     * to use both components.
     *
     * @return an array of two JPanels.
     */
    private JComponent[] createComponents() {
        JPanel listPanel = new JPanel(new BorderLayout());
        final JScrollPane scrollPane = new JScrollPane(inputPathsList);
        scrollPane.setPreferredSize(new Dimension(100, 50));
        listPanel.add(scrollPane, BorderLayout.CENTER);

        final JPanel addRemoveButtonPanel = new JPanel();
        int axis = this.xAxis ? BoxLayout.X_AXIS : BoxLayout.Y_AXIS;
        final BoxLayout buttonLayout = new BoxLayout(addRemoveButtonPanel, axis);
        addRemoveButtonPanel.setLayout(buttonLayout);
        addRemoveButtonPanel.add(createAddInputButton());
        addRemoveButtonPanel.add(createRemoveInputButton());

        JPanel[] panels = new JPanel[2];
        panels[0] = listPanel;
        panels[1] = addRemoveButtonPanel;

        return panels;
    }

    /**
     * Clears the list of source products.
     */
    public void clear() {
        listModel.clear();
    }

    /**
     * Allows clients to add single products.
     *
     * @param product A product to add.
     */
    public void addProduct(Product product) {
        if (product != null) {
            try {
                listModel.addElements(product);
            } catch (ValidationException ve) {
                Debug.trace(ve);
            }
        }
    }

    /**
     * Returns those source products that have been chosen from the product tree.
     *
     * @return An array of source products.
     */
    public Product[] getSourceProducts() {
        return listModel.getSourceProducts();
    }

    private JList<Object> createInputPathsList(InputListModel inputListModel) {
        JList<Object> list = new JList<>(inputListModel);
        list.setCellRenderer(new SourceProductListRenderer());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        return list;
    }

    /**
     * If set, users will be asked whether to use this pattern for recursively collecting products from directories.
     * To unset this, pass {@code null} as pattern.
     * @since SNAP 3.2
     *
     * @param pattern The pattern to be used when collecting products from directories
     */
    public void setDefaultPattern(String pattern) {
        this.defaultPattern = pattern;
    }

    private AbstractButton createAddInputButton() {
        final AbstractButton addButton = ToolButtonFactory.createButton(UIUtils.loadImageIcon("icons/Plus24.gif"),
                                                                        false);
        addButton.addActionListener(e -> {
            final JPopupMenu popup = new JPopupMenu("Add");
            final Rectangle buttonBounds = addButton.getBounds();
            final AddProductAction addProductAction = new AddProductAction(appContext, listModel);
            addProductAction.setProductFilter(productFilter);
            popup.add(addProductAction);
            popup.add(new AddFileAction(appContext, listModel, propertyNameLastOpenInputDir, propertyNameLastOpenedFormat));
            popup.add(new AddDirectoryAction(appContext, listModel, false, propertyNameLastOpenInputDir, defaultPattern));
            popup.add(new AddDirectoryAction(appContext, listModel, true, propertyNameLastOpenInputDir, defaultPattern));
            popup.show(addButton, 1, buttonBounds.height + 1);
        });
        return addButton;
    }

    private AbstractButton createRemoveInputButton() {
        final AbstractButton removeButton = ToolButtonFactory.createButton(UIUtils.loadImageIcon("icons/Minus24.gif"),
                                                                           false);
        removeButton.addActionListener(e -> listModel.removeElementsAt(inputPathsList.getSelectedIndices()));
        return removeButton;
    }

    @Override
    public void bindComponents() {
        String propertyName = getBinding().getPropertyName();
        Property property = getBinding().getContext().getPropertySet().getProperty(propertyName);
        Assert.argument(property.getType().equals(String[].class), "property '" + propertyName +"' must be of type String[].class");
        listModel.setProperty(property);
    }

    @Override
    public void unbindComponents() {
        listModel.setProperty(null);
    }

    @Override
    public void adjustComponents() {

    }

    /**
     * Add a listener that is informed every time the list's contents change.
     * @param changeListener the listener to add
     */
    public void addChangeListener(ListDataListener changeListener) {
        listModel.addListDataListener(changeListener);
    }

    /**
     * Remove a change listener
     * @param changeListener the listener to remove
     */
    public void removeChangeListener(ListDataListener changeListener) {
        listModel.removeListDataListener(changeListener);
    }

    /**
     * Add a listener that is informed every time the list's selection is changed.
     * @since SNAP 3.2
     * @param selectionListener the listener to add
     */
    public void addSelectionListener(ListSelectionListener selectionListener) {
        inputPathsList.addListSelectionListener(selectionListener);
    }

    /**
     * Remove a selection listener
     * @since SNAP 3.2
     *
     * @param selectionListener the listener to remove
     */
    public void removeSelectionListener(ListSelectionListener selectionListener) {
        inputPathsList.removeListSelectionListener(selectionListener);
    }

    /**
     * @since SNAP 3.2
     * @param object the object which may be selected or not
     * @return true, if the object is selected
     */
    public boolean isSelected(Object object) {
        return inputPathsList.isSelectedIndex(listModel.getIndexOf(object));
    }

    /**
     * The filter to be used to filter the list of opened products which are offered to the user for selection.
     * @param productFilter the filter
     */
    public void setProductFilter(ProductFilter productFilter) {
        this.productFilter = productFilter;
    }

    /**
     * Setter for property name indicating the last directory the user has opened
     *
     * @param propertyNameLastOpenedFormat property name indicating the last directory the user has opened
     */
    public void setPropertyNameLastOpenedFormat(String propertyNameLastOpenedFormat) {
        this.propertyNameLastOpenedFormat = propertyNameLastOpenedFormat;
    }

    /**
     * Setter for property name indicating the last product format the user has opened
     * @param propertyNameLastOpenInputDir property name indicating the last product format the user has opened
     */
    public void setPropertyNameLastOpenInputDir(String propertyNameLastOpenInputDir) {
        this.propertyNameLastOpenInputDir = propertyNameLastOpenInputDir;
    }

    /**
     * Setter for xAxis property.
     *
     * @param xAxis {@code true} if the buttons on the second panel shall be laid out in horizontal direction
     */
    public void setXAxis(boolean xAxis) {
        this.xAxis = xAxis;
    }

    private static class SourceProductListRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String text;
            if (value instanceof File) {
                text = ((File) value).getAbsolutePath();
            } else {
                text = ((ProductNode) value).getDisplayName();
            }

            label.setText(text);
            label.setToolTipText(text);
            return label;
        }
    }
}
