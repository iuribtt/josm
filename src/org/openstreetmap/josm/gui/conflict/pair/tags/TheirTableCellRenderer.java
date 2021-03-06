// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.conflict.pair.tags;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.gui.conflict.ConflictColors;
import org.openstreetmap.josm.gui.conflict.pair.MergeDecisionType;

public class TheirTableCellRenderer extends TagMergeTableCellRenderer {

    protected void setBackgroundColor(TagMergeItem item, boolean isSelected) {
        if (isSelected) {
            setBackground(ConflictColors.BGCOLOR_SELECTED.get());
            return;
        }
        if (MergeDecisionType.KEEP_MINE.equals(item.getMergeDecision())) {
            setBackground(ConflictColors.BGCOLOR_DROP.get());
        } else if (MergeDecisionType.KEEP_THEIR.equals(item.getMergeDecision())) {
            setBackground(ConflictColors.BGCOLOR_KEEP.get());
        } else if (MergeDecisionType.UNDECIDED.equals(item.getMergeDecision())) {
            setBackground(ConflictColors.BGCOLOR_UNDECIDED.get());
        }
    }

    protected void setTextColor(TagMergeItem item) {
        if (MergeDecisionType.KEEP_MINE.equals(item.getMergeDecision())) {
            setForeground(ConflictColors.FGCOLOR_DROP.get());
        } else if (MergeDecisionType.KEEP_THEIR.equals(item.getMergeDecision())) {
            setForeground(ConflictColors.FGCOLOR_KEEP.get());
        } else if (MergeDecisionType.UNDECIDED.equals(item.getMergeDecision())) {
            setForeground(ConflictColors.FGCOLOR_UNDECIDED.get());
        }
    }

    @Override
    protected void renderKey(TagMergeItem item, boolean isSelected) {
        setBackgroundColor(item, isSelected);
        setTextColor(item);
        if (item.getTheirTagValue() == null) {
            setText(tr("<undefined>"));
            setToolTipText(tr("Their dataset does not include a tag with key {0}", item.getKey()));
        } else {
            setText(item.getKey());
            setToolTipText(item.getKey());
        }
    }

    @Override
    protected void renderValue(TagMergeItem item, boolean isSelected) {
        setBackgroundColor(item,isSelected);
        setTextColor(item);
        if (item.getTheirTagValue() == null) {
            setText(tr("<undefined>"));
            setToolTipText(tr("Their dataset does not include a tag with key {0}", item.getKey()));
        } else {
            setText(item.getTheirTagValue());
            setToolTipText(item.getTheirTagValue());
        }
    }
}
