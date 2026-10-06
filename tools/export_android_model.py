"""Merge the existing common LoRA into its pinned base and export offline GGUF."""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', type=Path, default=Path('.artifacts/question-ai/base'))
    parser.add_argument('--adapter', type=Path, default=Path('models/question-author/v0.2-common-experimental'))
    parser.add_argument('--llama', type=Path, default=Path('.local-tools/llama.cpp'))
    args = parser.parse_args()
    source = json.loads((args.adapter / 'manifest.json').read_text(encoding='utf-8'))
    assert hashlib.sha256((args.base / 'model.safetensors').read_bytes()).hexdigest() == source['base_weights_sha256']
    assert hashlib.sha256((args.adapter / 'adapter_model.safetensors').read_bytes()).hexdigest() == source['files']['adapter_model.safetensors']['sha256']
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer
    from peft import PeftModel
    merged_dir = Path('.artifacts/question-ai/android-merged-v2')
    output = Path('app/src/main/assets/models/question-author-v2-q8.gguf')
    output.parent.mkdir(parents=True, exist_ok=True)
    sys.path.insert(0, str(Path('training/question_author').resolve()))
    from contract import SYSTEM, TOOLS
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True)
    template = tokenizer.apply_chat_template([{'role': 'system', 'content': SYSTEM}, {'role': 'user', 'content': '{{CONTEXT}}'}],
        tools=TOOLS, tokenize=False, add_generation_prompt=True, enable_thinking=False)
    (output.parent / 'question-prompt.txt').write_text(template, encoding='utf-8', newline='\r\n')
    cache_marker = merged_dir / 'merge-source.json'
    cache_key = {'base': source['base_weights_sha256'], 'adapter': source['files']['adapter_model.safetensors']['sha256']}
    if not cache_marker.exists() or json.loads(cache_marker.read_text()) != cache_key:
        model = AutoModelForCausalLM.from_pretrained(args.base, torch_dtype=torch.float32, local_files_only=True)
        model = PeftModel.from_pretrained(model, args.adapter, local_files_only=True).merge_and_unload()
        model.to(torch.float16).save_pretrained(merged_dir, safe_serialization=True)
        AutoTokenizer.from_pretrained(args.base, local_files_only=True).save_pretrained(merged_dir)
        cache_marker.write_text(json.dumps(cache_key), encoding='utf-8')
        del model
    subprocess.run([sys.executable, str(args.llama / 'convert_hf_to_gguf.py'), str(merged_dir), '--outtype', 'q8_0', '--outfile', str(output)], check=True)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    manifest = {'model': 'Qwen3-0.6B + common-v0.2 LoRA', 'quantization': 'Q8_0', 'sha256': digest,
                'file': output.name, 'bytes': output.stat().st_size,
                'adapter_sha256': hashlib.sha256((args.adapter / 'adapter_model.safetensors').read_bytes()).hexdigest()}
    (output.parent / 'question-author-v2.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(manifest), flush=True)

if __name__ == '__main__':
    main()
