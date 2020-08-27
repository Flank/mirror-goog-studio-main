# This file contains the currently active iml_project. In order to
# transition to go/unbundle-studio, we keep two set of macros that
# output rules with the same name. We use this file to switch which
# rules are in effect. This enables iml_module rules with
# the same value in their project attribute. The current acceptable
# values are "" (empty) for the default project, and "unb" for the
# unbundled one.
PROJECT = ""
