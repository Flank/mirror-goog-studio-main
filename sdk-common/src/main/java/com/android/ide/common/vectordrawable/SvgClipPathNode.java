/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

/**
 * Represents a SVG group element that contains a clip-path. SvgClipPathNode's mChildren will
 * contain the actual path data of the clip-path. The path of the clip will be constructed in
 * writeXML by concatenating mChildren's paths. mAffectedNodes contains any group or leaf nodes that
 * are clipped by the path.
 */
public class SvgClipPathNode extends SvgGroupNode {
    private final ArrayList<SvgNode> mAffectedNodes = new ArrayList<>();

    public SvgClipPathNode(@NonNull SvgTree svgTree, @NonNull Node docNode, @Nullable String name) {
        super(svgTree, docNode, name);
    }

    @Override
    @NotNull
    public SvgClipPathNode deepCopy() {
        SvgClipPathNode newInstance = new SvgClipPathNode(getTree(), getDocumentNode(), getName());
        newInstance.copyFrom(this);
        return newInstance;
    }

    protected void copyFrom(@NonNull SvgClipPathNode from) {
        super.copyFrom(from);
        for (SvgNode node : from.mAffectedNodes) {
            addAffectedNode(node);
        }
    }

    @Override
    public void addChild(@NonNull SvgNode child) {
        // Pass the presentation map down to the children, who can override the attributes.
        mChildren.add(child);
        // The child has its own attributes map. But the parents can still fill some attributes
        // if they don't exists
        child.fillEmptyAttributes(mVdAttributesMap);
    }

    public void addAffectedNode(@NonNull SvgNode child) {
        mAffectedNodes.add(child);
        child.fillEmptyAttributes(mVdAttributesMap);
    }

    @Override
    public void flatten(@NotNull AffineTransform transform) {
        for (SvgNode n : mChildren) {
            mStackedTransform.setTransform(transform);
            mStackedTransform.concatenate(mLocalTransform);
            n.flatten(mStackedTransform);
        }

        mStackedTransform.setTransform(transform);
        mStackedTransform.concatenate(mLocalTransform);

        if (mVdAttributesMap.containsKey(Svg2Vector.SVG_STROKE_WIDTH)
                && ((mStackedTransform.getType() & AffineTransform.TYPE_MASK_SCALE) != 0)) {
            getTree()
                    .logErrorLine(
                            "Scaling of the stroke width is ignored",
                            getDocumentNode(),
                            SvgTree.SvgLogLevel.WARNING);
        }
    }

    @Override
    public void transformIfNeeded(@NotNull AffineTransform rootTransform) {
        for (SvgNode p : mChildren) {
            p.transformIfNeeded(rootTransform);
        }
    }

    @Override
    public void writeXML(@NonNull OutputStreamWriter writer, boolean inClipPath,
            @NonNull String indent) throws IOException {
        writer.write(indent);
        writer.write("<group>");
        writer.write(System.lineSeparator());
        writer.write(indent);
        writer.write(INDENT_UNIT);
        writer.write("<clip-path android:pathData=\"");
        for (SvgNode node : mChildren) {
            node.writeXML(writer, true, indent + INDENT_UNIT);
        }
        writer.write("\"/>");
        writer.write(System.lineSeparator());
        for (SvgNode node : mAffectedNodes) {
            node.writeXML(writer, false, indent + INDENT_UNIT);
        }
        writer.write(indent);
        writer.write("</group>");
        writer.write(System.lineSeparator());
    }

    /**
     * Concatenates the affected nodes transformations to the clipPathNode's so it is properly
     * transformed.
     */
    public void setClipPathNodeAttributes() {
        for (SvgNode n : mAffectedNodes) {
            mLocalTransform.concatenate(n.mLocalTransform);
        }
    }
}
