#!/usr/bin/env python3
"""
Chessko – Xcode project generator
Run once from this directory:  python3 create_xcode_project.py
"""

import os
import uuid
import plistlib
from pathlib import Path

# ── Config ─────────────────────────────────────────────────────────────────
APP_NAME       = "Chessko"
BUNDLE_ID      = "com.veljkoni.chessko"
DEPLOYMENT_TGT = "18.0"          # iOS 18 minimum (runs on iOS 26 too)
SWIFT_VERSION  = "6.0"
TEAM_ID        = ""              # fill in your Apple Team ID if signing locally

ROOT = Path(__file__).parent
SRC  = ROOT / APP_NAME           # Swift source folder

# ── Collect Swift source files ──────────────────────────────────────────────
swift_files = sorted(SRC.rglob("*.swift"))
if not swift_files:
    print("ERROR: No .swift files found under", SRC)
    exit(1)

print(f"Found {len(swift_files)} Swift files:")
for f in swift_files:
    print(" ", f.relative_to(ROOT))

# ── Collect resource files (mp3, etc.) ──────────────────────────────────────
resource_files = sorted(SRC.glob("*.mp3"))   # top-level audio files
if resource_files:
    print(f"Found {len(resource_files)} resource file(s):")
    for f in resource_files:
        print(" ", f.relative_to(ROOT))

# ── UUID helpers ────────────────────────────────────────────────────────────
def uid():
    return uuid.uuid4().hex[:24].upper()

# ── Key objects ─────────────────────────────────────────────────────────────
PROJECT_UID      = uid()
MAIN_GROUP_UID   = uid()
SRC_GROUP_UID    = uid()
PRODUCTS_UID     = uid()
TARGET_UID       = uid()
APP_PRODUCT_UID  = uid()
BUILD_CFG_LIST_PROJECT = uid()
BUILD_CFG_LIST_TARGET  = uid()
DEBUG_PROJECT_UID      = uid()
RELEASE_PROJECT_UID    = uid()
DEBUG_TARGET_UID       = uid()
RELEASE_TARGET_UID     = uid()
SOURCES_PHASE_UID      = uid()
FRAMEWORKS_PHASE_UID   = uid()
RESOURCES_PHASE_UID    = uid()

# Assets.xcassets UIDs
ASSETS_FILEREF_UID  = uid()
ASSETS_BUILDFILE_UID = uid()

# Per-file UIDs
file_refs  = {}   # path -> fileRef UID
build_refs = {}   # path -> buildFile UID

for f in swift_files:
    file_refs[f]  = uid()
    build_refs[f] = uid()

# Resource file UIDs (mp3, etc.)
res_file_refs  = {}   # path -> fileRef UID
res_build_refs = {}   # path -> buildFile UID

for f in resource_files:
    res_file_refs[f]  = uid()
    res_build_refs[f] = uid()

# Group structure mirroring the folder layout
# We'll create sub-groups for Models/, Logic/, ViewModels/, Views/
sub_folders = {}
for f in swift_files:
    rel = f.relative_to(SRC)
    parts = rel.parts
    if len(parts) > 1:
        folder = parts[0]
        if folder not in sub_folders:
            sub_folders[folder] = uid()

# ── pbxproj sections ────────────────────────────────────────────────────────

def file_ref_section():
    lines = []
    for f, fuid in file_refs.items():
        lines.append(f'\t\t{fuid} = {{isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = "{f.name}"; sourceTree = "<group>"; }};')
    # Assets.xcassets
    lines.append(f'\t\t{ASSETS_FILEREF_UID} = {{isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = "<group>"; }};')
    # Resource files (mp3, etc.)
    for f, fuid in res_file_refs.items():
        lines.append(f'\t\t{fuid} = {{isa = PBXFileReference; lastKnownFileType = audio.mp3; path = "{f.name}"; sourceTree = "<group>"; }};')
    # App product
    lines.append(f'\t\t{APP_PRODUCT_UID} = {{isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = {APP_NAME}.app; sourceTree = BUILT_PRODUCTS_DIR; }};')
    return "\n".join(lines)

def build_file_section():
    lines = []
    for f, buid in build_refs.items():
        fuid = file_refs[f]
        lines.append(f'\t\t{buid} = {{isa = PBXBuildFile; fileRef = {fuid}; }};')
    # Assets.xcassets build file
    lines.append(f'\t\t{ASSETS_BUILDFILE_UID} = {{isa = PBXBuildFile; fileRef = {ASSETS_FILEREF_UID}; }};')
    # Resource build files (mp3, etc.)
    for f, buid in res_build_refs.items():
        fuid = res_file_refs[f]
        lines.append(f'\t\t{buid} = {{isa = PBXBuildFile; fileRef = {fuid}; }};')
    return "\n".join(lines)

def group_section():
    # Map each file to its sub-folder (or root if top-level)
    folder_children = {folder: [] for folder in sub_folders}
    root_children = []

    for f in swift_files:
        rel = f.relative_to(SRC)
        parts = rel.parts
        if len(parts) > 1:
            folder_children[parts[0]].append(file_refs[f])
        else:
            root_children.append(file_refs[f])

    lines = []

    # Sub-group entries
    for folder, gid in sub_folders.items():
        children_str = "\n".join(f"\t\t\t\t{c}," for c in folder_children[folder])
        lines.append(f"""
\t\t{gid} = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
{children_str}
\t\t\t);
\t\t\tpath = {folder};
\t\t\tsourceTree = "<group>";
\t\t}};""")

    # Source group (Chessko/)
    src_children = [f"\t\t\t\t{ASSETS_FILEREF_UID},"]
    src_children += [f"\t\t\t\t{c}," for c in root_children]
    src_children += [f"\t\t\t\t{fuid}," for fuid in res_file_refs.values()]
    src_children += [f"\t\t\t\t{gid}," for gid in sub_folders.values()]
    src_children_str = "\n".join(src_children)
    lines.append(f"""
\t\t{SRC_GROUP_UID} = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
{src_children_str}
\t\t\t);
\t\t\tpath = {APP_NAME};
\t\t\tsourceTree = "<group>";
\t\t}};""")

    # Products group
    lines.append(f"""
\t\t{PRODUCTS_UID} = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
\t\t\t\t{APP_PRODUCT_UID},
\t\t\t);
\t\t\tname = Products;
\t\t\tsourceTree = "<group>";
\t\t}};""")

    # Main group
    lines.append(f"""
\t\t{MAIN_GROUP_UID} = {{
\t\t\tisa = PBXGroup;
\t\t\tchildren = (
\t\t\t\t{SRC_GROUP_UID},
\t\t\t\t{PRODUCTS_UID},
\t\t\t);
\t\t\tsourceTree = "<group>";
\t\t}};""")

    return "\n".join(lines)

def sources_phase():
    files = "\n".join(f"\t\t\t\t{build_refs[f]}," for f in swift_files)
    return f"""
\t\t{SOURCES_PHASE_UID} = {{
\t\t\tisa = PBXSourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
{files}
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};"""

pbxproj = f"""// !$*UTF8*$!
{{
\tarchiveVersion = 1;
\tclasses = {{
\t}};
\tobjectVersion = 77;
\tobjects = {{

/* Begin PBXBuildFile section */
{build_file_section()}
/* End PBXBuildFile section */

/* Begin PBXFileReference section */
{file_ref_section()}
/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
\t\t{FRAMEWORKS_PHASE_UID} = {{
\t\t\tisa = PBXFrameworksBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
{group_section()}
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
\t\t{TARGET_UID} = {{
\t\t\tisa = PBXNativeTarget;
\t\t\tbuildConfigurationList = {BUILD_CFG_LIST_TARGET};
\t\t\tbuildPhases = (
\t\t\t\t{SOURCES_PHASE_UID},
\t\t\t\t{FRAMEWORKS_PHASE_UID},
\t\t\t\t{RESOURCES_PHASE_UID},
\t\t\t);
\t\t\tbuildRules = (
\t\t\t);
\t\t\tdependencies = (
\t\t\t);
\t\t\tname = {APP_NAME};
\t\t\tproductName = {APP_NAME};
\t\t\tproductReference = {APP_PRODUCT_UID};
\t\t\tproductType = "com.apple.product-type.application";
\t\t}};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
\t\t{PROJECT_UID} = {{
\t\t\tisa = PBXProject;
\t\t\tattributes = {{
\t\t\t\tBuildIndependentTargetsInParallel = 1;
\t\t\t\tLastSwiftUpdateCheck = 1600;
\t\t\t\tLastUpgradeCheck = 1600;
\t\t\t\tTargetAttributes = {{
\t\t\t\t\t{TARGET_UID} = {{
\t\t\t\t\t\tCreatedOnToolsVersion = 16.0;
\t\t\t\t\t}};
\t\t\t\t}};
\t\t\t}};
\t\t\tbuildConfigurationList = {BUILD_CFG_LIST_PROJECT};
\t\t\tcompatibilityVersion = "Xcode 15.0";
\t\t\tdevelopmentRegion = en;
\t\t\thasScannedForEncodings = 0;
\t\t\tknownRegions = (
\t\t\t\ten,
\t\t\t\tBase,
\t\t\t);
\t\t\tmainGroup = {MAIN_GROUP_UID};
\t\t\tproductRefGroup = {PRODUCTS_UID};
\t\t\tprojectDirPath = "";
\t\t\tprojectRoot = "";
\t\t\ttargets = (
\t\t\t\t{TARGET_UID},
\t\t\t);
\t\t}};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
\t\t{RESOURCES_PHASE_UID} = {{
\t\t\tisa = PBXResourcesBuildPhase;
\t\t\tbuildActionMask = 2147483647;
\t\t\tfiles = (
\t\t\t\t{ASSETS_BUILDFILE_UID},
{"".join(f"{chr(9)}{chr(9)}{chr(9)}{chr(9)}{buid},{chr(10)}" for buid in res_build_refs.values())}\t\t\t);
\t\t\trunOnlyForDeploymentPostprocessing = 0;
\t\t}};
/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
{sources_phase()}
/* End PBXSourcesBuildPhase section */

/* Begin XCBuildConfiguration section */
\t\t{DEBUG_PROJECT_UID} = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tALWAYS_SEARCH_USER_PATHS = NO;
\t\t\t\tASSET_CATALOG_COMPILER_OPTIMIZATION = space;
\t\t\t\tCLANG_ANALYZER_NONNULL = YES;
\t\t\t\tCLANG_ANALYZER_NUMBER_OBJECT_CONVERSION = YES_AGGRESSIVE;
\t\t\t\tCLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
\t\t\t\tCLANG_ENABLE_MODULES = YES;
\t\t\t\tCLANG_ENABLE_OBJC_ARC = YES;
\t\t\t\tCLANG_ENABLE_OBJC_WEAK = YES;
\t\t\t\tCLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
\t\t\t\tCLANG_WARN_BOOL_CONVERSION = YES;
\t\t\t\tCLANG_WARN_COMMA = YES;
\t\t\t\tCLANG_WARN_CONSTANT_CONVERSION = YES;
\t\t\t\tCLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
\t\t\t\tCLANG_WARN_DIRECT_OBJC_ISA_USAGE = YES_ERROR;
\t\t\t\tCLANG_WARN_DOCUMENTATION_COMMENTS = YES;
\t\t\t\tCLANG_WARN_EMPTY_BODY = YES;
\t\t\t\tCLANG_WARN_ENUM_CONVERSION = YES;
\t\t\t\tCLANG_WARN_INFINITE_RECURSION = YES;
\t\t\t\tCLANG_WARN_INT_CONVERSION = YES;
\t\t\t\tCLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
\t\t\t\tCLANG_WARN_OBJC_IMPLICIT_RETAIN_CYCLE = YES;
\t\t\t\tCLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
\t\t\t\tCLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
\t\t\t\tCLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
\t\t\t\tCLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
\t\t\t\tCLANG_WARN_STRICT_PROTOTYPES = YES;
\t\t\t\tCLANG_WARN_SUSPICIOUS_MOVE = YES;
\t\t\t\tCLANG_WARN_UNGUARDED_AVAILABILITY = YES_AGGRESSIVE;
\t\t\t\tCLANG_WARN_UNREACHABLE_CODE = YES;
\t\t\t\tCLANG_WARN__DUPLICATE_METHOD_FALLTHROUGH = YES;
\t\t\t\tCOPY_PHASE_STRIP = NO;
\t\t\t\tDEBUG_INFORMATION_FORMAT = dwarf;
\t\t\t\tENABLE_STRICT_OBJC_MSGSEND = YES;
\t\t\t\tENABLE_TESTABILITY = YES;
\t\t\t\tGCC_C_LANGUAGE_STANDARD = gnu17;
\t\t\t\tGCC_DYNAMIC_NO_PIC = NO;
\t\t\t\tGCC_NO_COMMON_BLOCKS = YES;
\t\t\t\tGCC_OPTIMIZATION_LEVEL = 0;
\t\t\t\tGCC_PREPROCESSOR_DEFINITIONS = (
\t\t\t\t\t"DEBUG=1",
\t\t\t\t\t"$(inherited)",
\t\t\t\t);
\t\t\t\tGCC_WARN_64_TO_32_BIT_CONVERSION = YES;
\t\t\t\tGCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
\t\t\t\tGCC_WARN_UNDECLARED_SELECTOR = YES;
\t\t\t\tGCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
\t\t\t\tGCC_WARN_UNUSED_FUNCTION = YES;
\t\t\t\tGCC_WARN_UNUSED_VARIABLE = YES;
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = {DEPLOYMENT_TGT};
\t\t\t\tMTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
\t\t\t\tMTL_FAST_MATH = YES;
\t\t\t\tONLY_ACTIVE_ARCH = YES;
\t\t\t\tSDKROOT = iphoneos;
\t\t\t\tSWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG $(inherited)";
\t\t\t\tSWIFT_OPTIMIZATION_LEVEL = "-Onone";
\t\t\t\tSWIFT_VERSION = {SWIFT_VERSION};
\t\t\t}};
\t\t\tname = Debug;
\t\t}};
\t\t{RELEASE_PROJECT_UID} = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tALWAYS_SEARCH_USER_PATHS = NO;
\t\t\t\tASSET_CATALOG_COMPILER_OPTIMIZATION = space;
\t\t\t\tCLANG_ANALYZER_NONNULL = YES;
\t\t\t\tCLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
\t\t\t\tCLANG_ENABLE_MODULES = YES;
\t\t\t\tCLANG_ENABLE_OBJC_ARC = YES;
\t\t\t\tCLANG_ENABLE_OBJC_WEAK = YES;
\t\t\t\tCOPY_PHASE_STRIP = NO;
\t\t\t\tDEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
\t\t\t\tENABLE_NS_ASSERTIONS = NO;
\t\t\t\tENABLE_STRICT_OBJC_MSGSEND = YES;
\t\t\t\tGCC_C_LANGUAGE_STANDARD = gnu17;
\t\t\t\tGCC_NO_COMMON_BLOCKS = YES;
\t\t\t\tGCC_WARN_64_TO_32_BIT_CONVERSION = YES;
\t\t\t\tGCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
\t\t\t\tGCC_WARN_UNDECLARED_SELECTOR = YES;
\t\t\t\tGCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
\t\t\t\tGCC_WARN_UNUSED_FUNCTION = YES;
\t\t\t\tGCC_WARN_UNUSED_VARIABLE = YES;
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = {DEPLOYMENT_TGT};
\t\t\t\tMTL_FAST_MATH = YES;
\t\t\t\tSDKROOT = iphoneos;
\t\t\t\tSWIFT_COMPILATION_MODE = wholemodule;
\t\t\t\tSWIFT_OPTIMIZATION_LEVEL = "-O";
\t\t\t\tSWIFT_VERSION = {SWIFT_VERSION};
\t\t\t\tVALIDATE_PRODUCT = YES;
\t\t\t}};
\t\t\tname = Release;
\t\t}};
\t\t{DEBUG_TARGET_UID} = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
\t\t\t\tASSSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
\t\t\t\tCODE_SIGN_STYLE = Automatic;
\t\t\t\tCURRENT_PROJECT_VERSION = 1;
\t\t\t\tDEVELOPMENT_ASSET_PATHS = "";
\t\t\t\tENABLE_PREVIEWS = YES;
\t\t\t\tGENERATE_INFOPLIST_FILE = YES;
\t\t\t\tINFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES;
\t\t\t\tINFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents = YES;
\t\t\t\tINFOPLIST_KEY_UILaunchScreen_Generation = YES;
\t\t\t\tINFOPLIST_KEY_UISupportedInterfaceOrientations_iPad = "UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
\t\t\t\tINFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone = "UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = {DEPLOYMENT_TGT};
\t\t\t\tLE_SWIFT_VERSION = {SWIFT_VERSION};
\t\t\t\tMARKETING_VERSION = 1.0;
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = {BUNDLE_ID};
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSDKROOT = iphoneos;
\t\t\t\tSWIFT_EMIT_LOC_STRINGS = YES;
\t\t\t\tSWIFT_VERSION = {SWIFT_VERSION};
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";
\t\t\t}};
\t\t\tname = Debug;
\t\t}};
\t\t{RELEASE_TARGET_UID} = {{
\t\t\tisa = XCBuildConfiguration;
\t\t\tbuildSettings = {{
\t\t\t\tASSSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;
\t\t\t\tASSSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
\t\t\t\tCODE_SIGN_STYLE = Automatic;
\t\t\t\tCURRENT_PROJECT_VERSION = 1;
\t\t\t\tDEVELOPMENT_ASSET_PATHS = "";
\t\t\t\tENABLE_PREVIEWS = YES;
\t\t\t\tGENERATE_INFOPLIST_FILE = YES;
\t\t\t\tINFOPLIST_KEY_UIApplicationSceneManifest_Generation = YES;
\t\t\t\tINFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents = YES;
\t\t\t\tINFOPLIST_KEY_UILaunchScreen_Generation = YES;
\t\t\t\tINFOPLIST_KEY_UISupportedInterfaceOrientations_iPad = "UIInterfaceOrientationPortrait UIInterfaceOrientationPortraitUpsideDown UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
\t\t\t\tINFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone = "UIInterfaceOrientationPortrait UIInterfaceOrientationLandscapeLeft UIInterfaceOrientationLandscapeRight";
\t\t\t\tIPHONEOS_DEPLOYMENT_TARGET = {DEPLOYMENT_TGT};
\t\t\t\tLE_SWIFT_VERSION = {SWIFT_VERSION};
\t\t\t\tMARKETING_VERSION = 1.0;
\t\t\t\tPRODUCT_BUNDLE_IDENTIFIER = {BUNDLE_ID};
\t\t\t\tPRODUCT_NAME = "$(TARGET_NAME)";
\t\t\t\tSDKROOT = iphoneos;
\t\t\t\tSWIFT_EMIT_LOC_STRINGS = YES;
\t\t\t\tSWIFT_VERSION = {SWIFT_VERSION};
\t\t\t\tTARGETED_DEVICE_FAMILY = "1,2";
\t\t\t}};
\t\t\tname = Release;
\t\t}};
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
\t\t{BUILD_CFG_LIST_PROJECT} = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{DEBUG_PROJECT_UID},
\t\t\t\t{RELEASE_PROJECT_UID},
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
\t\t{BUILD_CFG_LIST_TARGET} = {{
\t\t\tisa = XCConfigurationList;
\t\t\tbuildConfigurations = (
\t\t\t\t{DEBUG_TARGET_UID},
\t\t\t\t{RELEASE_TARGET_UID},
\t\t\t);
\t\t\tdefaultConfigurationIsVisible = 0;
\t\t\tdefaultConfigurationName = Release;
\t\t}};
/* End XCConfigurationList section */

\t}};
\trootObject = {PROJECT_UID};
}}
"""

# ── Write files ──────────────────────────────────────────────────────────────
xcodeproj_dir = ROOT / f"{APP_NAME}.xcodeproj"
xcodeproj_dir.mkdir(exist_ok=True)

pbxproj_path = xcodeproj_dir / "project.pbxproj"
pbxproj_path.write_text(pbxproj, encoding="utf-8")
print(f"\n✅  Created {pbxproj_path.relative_to(ROOT)}")
print(f"\n👉  Open {APP_NAME}.xcodeproj in Xcode 26 to build & run.")
print("    Select an iPhone simulator → ▶ Run")
