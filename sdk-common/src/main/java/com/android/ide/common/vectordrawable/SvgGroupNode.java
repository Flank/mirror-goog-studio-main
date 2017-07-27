/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.vectordrawable;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.w3c.dom.Node;

/**
 * Represent a SVG file's group element.
 */
class SvgGroupNode extends SvgNode {
    private static final Logger logger = Logger.getLogger(SvgGroupNode.class.getSimpleName());
    private static final String INDENT_LEVEL = "    ";

    protected final ArrayList<SvgNode> mChildren = new ArrayList<>();

    public SvgGroupNode(SvgTree svgTree, Node docNode, String name) {
        super(svgTree, docNode, name);
    }

    @Override
    public SvgGroupNode deepCopy() {
        SvgGroupNode newInstance = new SvgGroupNode(getTree(), getDocumentNode(), getName());
        for (SvgNode n : mChildren) {
            SvgNode m = n.deepCopy();
            newInstance.addChild(m);
        }
        newInstance.fillEmptyAttributes(mVdAttributesMap);
        newInstance.mLocalTransform = (AffineTransform) mLocalTransform.clone();
        return newInstance;
    }

    public void addChild(SvgNode child) {
        // Pass the presentation map down to the children, who can override the attributes.
        mChildren.add(child);
        // The child has its own attributes map. But the parents can still fill some attributes
        // if they don't exists
        child.fillEmptyAttributes(mVdAttributesMap);
    }

    public void removeChild(SvgNode child) {
        if (mChildren.contains(child)) {
            mChildren.remove(child);
        }
    }

    @Override
    public void dumpNode(String indent) {
        // Print the current group.
        logger.log(Level.FINE, indent + "current group is :" + getName());

        // Then print all the children.
        for (SvgNode node : mChildren) {
            node.dumpNode(indent + INDENT_LEVEL);
        }
    }

    @Override
    public boolean isGroupNode() {
        return true;
    }

    @Override
    public void transformIfNeeded(AffineTransform rootTransform) {
        for (SvgNode p : mChildren) {
            p.transformIfNeeded(rootTransform);
        }
    }

    @Override
    public void flatten(AffineTransform transform) {
        for (SvgNode n : mChildren) {
            mStackedTransform.setTransform(transform);
            mStackedTransform.concatenate(mLocalTransform);
            n.flatten(mStackedTransform);
        }
    }

    @Override
    public void writeXML(OutputStreamWriter writer, boolean inClipPath) throws IOException {
        for (SvgNode node : mChildren) {
            node.writeXML(writer, inClipPath);
        }
    }

    @Override
    public void fillPresentationAttributes(String name, String value) {
        for (SvgNode n : mChildren) {
            n.fillPresentationAttributes(name, value);
        }
    }
}
