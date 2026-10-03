package com.cinescout.scouting;

/**
 * How many assessed venues a run's filters left out, by reason.
 *
 * @param outsideRadius   placed on the map further from the base point than the radius
 * @param overBudget      their page gives a day price above the maximum budget
 * @param excludedType    of a kind the filters leave out
 * @param privateProperty private property, when the filters want none
 */
public record FilteredOut(int outsideRadius, int overBudget, int excludedType, int privateProperty) {

    public static final FilteredOut NONE = new FilteredOut(0, 0, 0, 0);

    public FilteredOut plusOutsideRadius(int more) {
        return new FilteredOut(outsideRadius + more, overBudget, excludedType, privateProperty);
    }

    public int total() {
        return outsideRadius + overBudget + excludedType + privateProperty;
    }
}
