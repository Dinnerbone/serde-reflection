// Copyright (c) Facebook, Inc. and its affiliates
// SPDX-License-Identifier: MIT OR Apache-2.0

use serde_generate::{cpp, python3, rust, test_utils, SourceInstaller};
use std::fs::File;
use std::io::Write;
use std::process::Command;
use tempfile::tempdir;

#[test]
fn test_that_python_code_parses() {
    let registry = test_utils::get_registry().unwrap();
    let dir = tempdir().unwrap();
    let source_path = dir.path().join("test.py");
    let mut source = File::create(&source_path).unwrap();
    python3::output(&mut source, &registry).unwrap();

    let python_path = format!(
        "{}:runtime/python",
        std::env::var("PYTHONPATH").unwrap_or_default()
    );
    let status = Command::new("python3")
        .arg(source_path)
        .env("PYTHONPATH", python_path)
        .status()
        .unwrap();
    assert!(status.success());
}

#[test]
fn test_that_installed_python_code_passes_pyre_check() {
    let registry = test_utils::get_registry().unwrap();
    let dir = tempdir().unwrap();

    let installer =
        python3::Installer::new(dir.path().join("src").into(), /* serde package */ None);
    installer.install_module("test", &registry).unwrap();
    installer.install_serde_runtime().unwrap();
    installer.install_bincode_runtime().unwrap();
    installer.install_lcs_runtime().unwrap();

    // Sadly, we have to manage numpy typeshed manually for now until the next release of numpy.
    let status = Command::new("cp")
        .arg("-r")
        .arg("runtime/python/typeshed")
        .arg(dir.path())
        .status()
        .unwrap();
    assert!(status.success());

    let mut pyre_config = File::create(dir.path().join(".pyre_configuration")).unwrap();
    writeln!(
        &mut pyre_config,
        r#"{{
  "source_directories": [
    "src"
  ],
  "search_path": [
    "typeshed"
  ]
}}"#,
    )
    .unwrap();

    let status = Command::new("pyre")
        .current_dir(dir.path())
        .arg("check")
        .status()
        .unwrap();
    assert!(status.success());
}

#[test]
fn test_that_cpp_code_compiles() {
    let registry = test_utils::get_registry().unwrap();
    let dir = tempdir().unwrap();
    let source_path = dir.path().join("test.cpp");
    let mut source = File::create(&source_path).unwrap();
    cpp::output(&mut source, &registry).unwrap();

    let output = Command::new("clang++")
        .arg("--std=c++17")
        .arg("-c")
        .arg("-o")
        .arg(dir.path().join("test.o"))
        .arg("-I")
        .arg("runtime/cpp")
        .arg(source_path)
        .output()
        .unwrap();
    assert_eq!(String::new(), String::from_utf8_lossy(&output.stderr));
    assert!(output.status.success());
}

// Quick test using rustc directly.
#[test]
fn test_that_rust_code_compiles() {
    let registry = test_utils::get_registry().unwrap();
    let dir = tempdir().unwrap();
    let source_path = dir.path().join("test.rs");
    let mut source = File::create(&source_path).unwrap();
    // Placeholder for serde_bytes::ByteBuf.
    writeln!(
        &mut source,
        "pub mod serde_bytes {{ pub type ByteBuf = Vec<u8>; }}\n"
    )
    .unwrap();
    rust::output(&mut source, /* with_derive_macros */ false, &registry).unwrap();

    let output = Command::new("rustc")
        .current_dir(dir.path())
        .arg("--crate-type")
        .arg("lib")
        .arg("--edition")
        .arg("2018")
        .arg(source_path)
        .output()
        .unwrap();
    assert_eq!(String::new(), String::from_utf8_lossy(&output.stderr));
    assert!(output.status.success());
}

// Full test using cargo. This may take a while.
#[test]
fn test_that_rust_code_compiles_with_derive_macros() {
    let registry = test_utils::get_registry().unwrap();
    let dir = tempdir().unwrap();
    std::fs::write(
        dir.path().join("Cargo.toml"),
        r#"[package]
name = "testing"
version = "0.1.0"
edition = "2018"

[dependencies]
serde = { version = "1.0", features = ["derive"] }
serde_bytes = "0.11"

[workspace]
"#,
    )
    .unwrap();
    std::fs::create_dir(dir.path().join("src")).unwrap();
    let source_path = dir.path().join("src/lib.rs");
    let mut source = File::create(&source_path).unwrap();
    rust::output(&mut source, /* with_derive_macros */ true, &registry).unwrap();
    // Use a stable `target` dir to avoid downloading and recompiling crates everytime.
    let target_dir = std::env::current_dir().unwrap().join("../target");
    let output = Command::new("cargo")
        .current_dir(dir.path())
        .arg("build")
        .arg("--target-dir")
        .arg(target_dir)
        .output()
        .unwrap();
    assert!(output.status.success());
}
