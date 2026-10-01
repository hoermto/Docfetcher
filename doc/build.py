#!/usr/bin/env python3

import shutil
from datetime import datetime
from pathlib import Path
import markdown

DOC_ROOT = Path(__file__).parent

LOCALE_TO_DISPLAY_NAME = {
	"en": "English",
	"de": "German",
	"it": "Italian",
	"ja": "Japanese",
	"tr": "Turkish",
	"uk": "Ukrainian",
	"zh": "Chinese",
	"zh_CN": "Chinese (China)",
	"zh_SG": "Chinese (Singapore)",
	"zh_HK": "Chinese (Hong Kong)",
	"zh_TW": "Chinese (Taiwan)",
}

STEM_TO_PROPS_KEY = {
	"Quick_Start": "quick_start",
	"Query_Syntax": "query_syntax",
	"Portable_Repositories": "portable_repos",
	"Indexing_Options": "indexing_options",
	"Regular_Expressions": "regular_expressions",
	"Release_Notification": "release_notification",
	"Memory_Limit": "memory_limit",
	"Watch_Limit": "watch_limit",
	"Preferences": "preferences",
}


def read_properties(path: Path) -> dict[str, str]:
	result = {}
	for line in path.read_text(encoding="utf-8").splitlines():
		line = line.strip()
		if not line or line.startswith("#"):
			continue
		if "=" in line:
			key, _, value = line.partition("=")
			result[key.strip()] = value.strip()
	return result


def fill_template(template: str, replacements: dict[str, str]) -> str:
	for key, value in replacements.items():
		template = template.replace("${" + key + "}", value)
	return template


def convert_markdown(path: Path) -> str:
	return markdown.markdown(path.read_text(encoding="utf-8"), extensions=["tables", "fenced_code"])


def main() -> None:
	src_dir = DOC_ROOT / "src"
	output_dir = DOC_ROOT / "output"
	all_dir = src_dir / "all"
	version = (DOC_ROOT / "../current-version.txt").read_text(encoding="utf-8").strip()
	year = str(datetime.now().year)

	template_main = (src_dir / "template-mainpage.html").read_text(encoding="utf-8")
	template_sub = (src_dir / "template-subpage.html").read_text(encoding="utf-8")

	for locale, display_name in LOCALE_TO_DISPLAY_NAME.items():
		locale_dir = src_dir / locale
		if not locale_dir.is_dir():
			print(f"Warning: locale directory not found: {locale_dir}")
			continue

		props = read_properties(locale_dir / "page.properties")
		props["year"] = year

		out_lang_dir = output_dir / display_name
		out_files_dir = out_lang_dir / "DocFetcher_Manual_files"
		out_files_dir.mkdir(parents=True, exist_ok=True)

		# Build main page
		main_md = locale_dir / "DocFetcher_Manual.markdown"
		contents = convert_markdown(main_md)
		main_footer = props.get("main_footer", "").replace("${year}", year)
		html = fill_template(template_main, {
			"docfetcher_manual": props.get("docfetcher_manual", ""),
			"author": props.get("author", ""),
			"app_name": "DocFetcher",
			"version": version,
			"contents": contents,
			"main_footer": main_footer,
		})
		(out_lang_dir / "DocFetcher_Manual.html").write_text(html, encoding="utf-8")

		# Build subpages
		files_src_dir = locale_dir / "DocFetcher_Manual_files"
		if files_src_dir.is_dir():
			for md_file in sorted(files_src_dir.glob("*.markdown")):
				stem = md_file.stem
				props_key = STEM_TO_PROPS_KEY.get(stem, stem.lower())
				subpage_title = props.get(props_key, stem)
				contents = convert_markdown(md_file)
				html = fill_template(template_sub, {
					"docfetcher_manual": props.get("docfetcher_manual", ""),
					"subpage_title": subpage_title,
					"author": props.get("author", ""),
					"contents": contents,
					"back_to_main_page": props.get("back_to_main_page", "Back to main page"),
					"back_link": "../DocFetcher_Manual.html",
				})
				(out_files_dir / (stem + ".html")).write_text(html, encoding="utf-8")

		# Copy shared assets
		for asset in ["style.css", "bullet.png"]:
			src_asset = all_dir / asset
			if src_asset.is_file():
				shutil.copyfile(src_asset, out_files_dir / asset)

		print(f"Built: {display_name}")

	print("Documentation build complete.")


if __name__ == "__main__":
	main()
