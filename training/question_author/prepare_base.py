"""Fetch a pinned public base model once, or verify an existing offline copy."""
import argparse
from hashlib import sha256
import json
from pathlib import Path
from urllib.request import urlopen

WEIGHTS_SHA256 = "f47f71177f32bcd101b7573ec9171e6a57f4f4d31148d38e382306f42996874b"
FILES = ("config.json", "generation_config.json", "tokenizer.json", "tokenizer_config.json",
         "vocab.json", "merges.txt", "model.safetensors", "LICENSE")


def verify(base, manifest, config):
    if manifest["sha"] != config["base_revision"]:
        raise ValueError("Unexpected base revision")
    record = next(f for f in manifest["siblings"] if f["rfilename"] == "model.safetensors")
    if record["lfs"]["sha256"] != WEIGHTS_SHA256:
        raise ValueError("Unexpected upstream weight checksum")
    for name in FILES:
        if not (base / name).is_file():
            raise ValueError(f"Missing base file: {name}")
    digest = sha256()
    with (base / "model.safetensors").open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != WEIGHTS_SHA256:
        raise ValueError("Downloaded weights do not match the pinned original")
    return {"model": config["base_model"], "revision": config["base_revision"],
            "weights_sha256": digest.hexdigest(), "verified": True}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--verify-only", action="store_true", help="Read local files only")
    args = parser.parse_args()
    config = json.loads(Path(__file__).with_name("model_config.json").read_text())
    if args.verify_only:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    else:
        from huggingface_hub import snapshot_download
        url = f"https://huggingface.co/api/models/{config['base_model']}/revision/{config['base_revision']}?blobs=true"
        with urlopen(url, timeout=60) as response:
            manifest = json.load(response)
        if manifest["sha"] != config["base_revision"]:
            raise ValueError("Refusing a different model revision")
        snapshot_download(repo_id=config["base_model"], revision=config["base_revision"],
                          local_dir=args.base, allow_patterns=list(FILES), max_workers=2)
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        args.manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(verify(args.base, manifest, config), indent=2))
