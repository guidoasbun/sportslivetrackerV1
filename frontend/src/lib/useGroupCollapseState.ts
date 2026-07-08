"use client";

import { useState, useCallback, useRef } from 'react';

const STORAGE_KEY_PREFIX = 'fixture-group-state-';

type CollapseState = Record<string, boolean>;

/**
 * Checks whether sessionStorage is available.
 * Returns false in private browsing modes or when storage is disabled.
 */
function isSessionStorageAvailable(): boolean {
    try {
        const testKey = '__session_storage_test__';
        sessionStorage.setItem(testKey, '1');
        sessionStorage.removeItem(testKey);
        return true;
    } catch {
        return false;
    }
}

/**
 * Reads the persisted collapse state for a given group label from sessionStorage.
 * Returns true if expanded (default), false if collapsed.
 */
function readPersistedState(groupLabel: string): boolean {
    try {
        const value = sessionStorage.getItem(`${STORAGE_KEY_PREFIX}${groupLabel}`);
        if (value === 'collapsed') return false;
        // Default to expanded for "expanded" value or no key
        return true;
    } catch {
        return true;
    }
}

/**
 * Writes the collapse state for a group label to sessionStorage.
 */
function writePersistedState(groupLabel: string, isExpanded: boolean): void {
    try {
        sessionStorage.setItem(
            `${STORAGE_KEY_PREFIX}${groupLabel}`,
            isExpanded ? 'expanded' : 'collapsed'
        );
    } catch {
        // sessionStorage unavailable — silently ignore
    }
}

/**
 * Hook to manage collapse state for region groups with sessionStorage persistence.
 *
 * - On initialization, reads persisted state for each provided group label.
 * - Defaults to expanded if no persisted state exists.
 * - On toggle, writes new state to sessionStorage.
 * - Gracefully handles sessionStorage unavailability (falls back to all-expanded, no persistence).
 *
 * @param groupLabels - Array of group label strings to manage state for.
 * @returns Object with `isExpanded(label)` check and `toggle(label)` function.
 */
export function useGroupCollapseState(groupLabels: string[]) {
    const storageAvailable = useRef(isSessionStorageAvailable());

    const [collapseState, setCollapseState] = useState<CollapseState>(() => {
        const initial: CollapseState = {};
        for (const label of groupLabels) {
            if (storageAvailable.current) {
                initial[label] = readPersistedState(label);
            } else {
                initial[label] = true; // default expanded
            }
        }
        return initial;
    });

    const isExpanded = useCallback(
        (label: string): boolean => {
            if (label in collapseState) {
                return collapseState[label];
            }
            // For labels not yet tracked, read from storage or default to expanded
            if (storageAvailable.current) {
                return readPersistedState(label);
            }
            return true;
        },
        [collapseState]
    );

    const toggle = useCallback(
        (label: string): void => {
            setCollapseState((prev) => {
                const currentExpanded = label in prev
                    ? prev[label]
                    : (storageAvailable.current ? readPersistedState(label) : true);
                const newExpanded = !currentExpanded;

                if (storageAvailable.current) {
                    writePersistedState(label, newExpanded);
                }

                return { ...prev, [label]: newExpanded };
            });
        },
        []
    );

    return { isExpanded, toggle };
}
