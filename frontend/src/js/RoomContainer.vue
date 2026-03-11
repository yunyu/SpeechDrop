<template>
    <transition name="fade">
        <a
            v-if="fileList.length > 1"
            onclick="ga('send', 'event', 'Room', 'archive')"
            :href="`/${roomId}/archive`"
            data-tooltip="Download all files"
            class="button download-all-button tooltip-left"
            download
        >
            <img
                :src="downloadFolderIcon"
                class="header-button-img"
                alt="Download icon"
            />
        </a>
    </transition>
    <div class="room-header">
        <span class="room-name" v-html="roomName"></span>
        <div class="room-divider"></div>
        <span class="room-code">{{ roomId }}</span>
    </div>

    <div class="folder-grid">
        <section v-for="folder in folderConfig" :key="folder.id" class="folder-section card">
            <header class="folder-header">
                <h3>{{ folder.label }}</h3>
            </header>
            <div class="folder-upload-slot">
                <div :id="`file-dropzone-${folder.id}`" class="folder-dropzone">
                    <img
                        class="passthrough-pointer"
                        :src="uploadIcon"
                        width="40"
                        height="40"
                        alt="Upload icon"
                    />
                    <p class="upload-text passthrough-pointer"></p>
                </div>
            </div>
            <transition-group
                :id="`folder-grid-${folder.id}`"
                name="grid-entries"
                class="folder-files"
                tag="div"
            >
                <div class="card-wrapper" v-for="fileEntry in groupedFiles[folder.id]" :key="fileEntry.origPos">
                    <article class="card file-card">
                        <div @click.self="promptDelete(fileEntry)" class="delete-button">&#215;</div>
                        <a :href="fileEntry.url" download>
                            <div class="file-thumbnail">
                                <iframe
                                    :src="fileEntry.thumbnailUrl"
                                    title="Document thumbnail"
                                    loading="lazy"
                                    sandbox="allow-same-origin allow-scripts"
                                ></iframe>
                            </div>
                        </a>
                        <a :href="fileEntry.url" download>
                            <header class="file-header">
                                <h3>{{ fileEntry.name }}</h3>
                                <div class="file-date">{{ fileEntry.formattedDate }}</div>
                            </header>
                        </a>
                        <div class="card-button-container">
                            <a
                                target="_blank"
                                rel="noopener"
                                :href="fileEntry.viewerUrl"
                                class="half card-button card-button-left"
                            >Preview</a>
                            <a :href="fileEntry.url" class="half card-button" download>Download</a>
                        </div>
                    </article>
                </div>
            </transition-group>
        </section>
    </div>

    <div class="modal">
        <input id="modal_1" type="checkbox" :checked="confirmDeleteFile !== null" @change="onConfirmModalChange" />
        <label for="modal_1" class="overlay"></label>
        <article>
            <header>
                <h3>Delete file?</h3>
                <span class="close delete-button" @click="cancelDelete">&times;</span>
            </header>
            <section class="content">
                Are you sure you want to delete <strong>{{ confirmDeleteFile && confirmDeleteFile.name }}</strong>?
            </section>
            <footer>
                <button class="button dangerous" @click="confirmDelete">Delete</button>
                <button class="button pseudo cancel-button" @click="cancelDelete">Cancel</button>
            </footer>
        </article>
    </div>
</template>

<script>
import Dropzone from 'dropzone';
import Cookies from 'js-cookie';
import EventBus from 'vertx3-eventbus-client';

import downloadFolderIcon from '../static/img/download-folder.svg';
import uploadIcon from '../static/img/upload-icon.svg';

Dropzone.autoDiscover = false;

const roomConfig = window.roomConfig;
const folderConfig = [
    { id: 'aff', label: 'Aff' },
    { id: 'neg', label: 'Neg' },
    { id: 'other', label: 'Other' }
];

const getCsrfToken = () => Cookies.get('XSRF-TOKEN');

const normalizeFolder = folder => {
    const normalized = String(folder || '').toLowerCase();
    if (normalized === 'aff' || normalized === 'neg' || normalized === 'other') {
        return normalized;
    }
    return 'other';
};

const buildViewerUrl = fileUrl => `https://docs.google.com/gview?embedded=true&url=${encodeURIComponent(fileUrl)}`;

export default {
    name: 'RoomContainer',
    data() {
        return {
            files: roomConfig.initialFiles.slice(),
            confirmDeleteFile: null,
            roomName: roomConfig.roomName,
            roomId: roomConfig.roomId,
            mediaUrl: roomConfig.mediaUrl,
            allowedMimes: roomConfig.allowedMimes,
            prevFilesJson: JSON.stringify(roomConfig.initialFiles),
            _onKeydown: null,
            folderConfig,
            downloadFolderIcon,
            uploadIcon
        };
    },
    mounted() {
        if (this.roomId) {
            ga('send', 'event', 'Room', 'join', this.roomId);
        }

        // Token refresh
        setInterval(() => {
            const r = new XMLHttpRequest();
            r.open('GET', `/${this.roomId}/index`, true);
            r.send();
        }, 600000);

        const eb = new EventBus('/sock');
        eb.onopen = () => {
            eb.registerHandler(`speechdrop.room.${this.roomId}`, (e, m) => this.updateFiles(m.body));
        };
        eb.enableReconnect(true);

        const setUploadText = (dropzoneElement, text) => {
            const textNode = dropzoneElement.getElementsByTagName('p')[0];
            if (textNode) {
                textNode.innerHTML = text;
            }
        };

        const resetDropzone = dropzoneElement => {
            setUploadText(dropzoneElement, 'Drag files here or click to upload');
            dropzoneElement.className = 'folder-dropzone dz-clickable';
        };

        const promptForRename = file => {
            const proposed = window.prompt('Rename file before upload (optional):', file.name);
            if (proposed === null) {
                return file.name;
            }
            return proposed.trim() || file.name;
        };

        this.$nextTick(() => {
            this.folderConfig.forEach(folder => {
                const dropCard = new Dropzone(`div#file-dropzone-${folder.id}`, {
                    url: `/${this.roomId}/upload`,
                    addedfile(file) {
                        file._speechdropRenamedName = promptForRename(file);
                    },
                    uploadprogress: (file, progress) => {
                        setUploadText(dropCard.element, `Uploading (${Math.floor(progress)}%)`);
                    },
                    success: (file, successMsg) => {
                        resetDropzone(dropCard.element);
                        setUploadText(dropCard.element, 'Upload successful!');
                        dropCard.element.className += ' dropzone-success';
                        this.updateFiles(JSON.stringify(successMsg));
                        setTimeout(() => {
                            resetDropzone(dropCard.element);
                        }, 2000);
                    },
                    error: (file, errorMsg) => {
                        resetDropzone(dropCard.element);
                        setUploadText(dropCard.element, 'Upload failed, please retry.');
                        dropCard.element.className += ' dropzone-fail';
                        console.log(errorMsg);
                        setTimeout(() => {
                            resetDropzone(dropCard.element);
                        }, 2000);
                    },
                    sending: (file, xhr, formData) => {
                        formData.append('X-XSRF-TOKEN', getCsrfToken());
                        formData.append('folder', folder.id);
                        formData.append('renamedFile', file._speechdropRenamedName || file.name);
                        ga('send', 'event', 'Room', 'upload', this.roomId);
                    },
                    createImageThumbnails: false,
                    maxFilesize: 10,
                    uploadMultiple: false,
                    acceptedFiles: this.allowedMimes
                });
                resetDropzone(dropCard.element);
            });
        });

        // Close confirmation modal on Escape key
        this._onKeydown = e => {
            const isEscape = e.key === 'Escape' || e.keyCode === 27;
            if (isEscape) {
                this.confirmDeleteFile = null;
            }
        };
        document.addEventListener('keydown', this._onKeydown);
    },
    beforeUnmount() {
        if (this._onKeydown) {
            document.removeEventListener('keydown', this._onKeydown);
        }
    },
    methods: {
        deleteFile(fileIndex) {
            const r = new XMLHttpRequest();
            r.open('POST', `/${this.roomId}/delete`, true);
            this.files.splice(fileIndex, 1, null);
            const data = `fileIndex=${fileIndex}`;
            r.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            r.setRequestHeader('X-XSRF-TOKEN', getCsrfToken());
            r.onload = () => this.updateFiles(r.response);
            r.send(data);
            ga('send', 'event', 'Room', 'delete', this.roomId);
        },
        promptDelete(fileEntry) {
            this.confirmDeleteFile = fileEntry;
        },
        confirmDelete() {
            if (this.confirmDeleteFile !== null) {
                this.deleteFile(this.confirmDeleteFile.origPos);
                this.confirmDeleteFile = null;
            }
        },
        cancelDelete() {
            this.confirmDeleteFile = null;
        },
        onConfirmModalChange(e) {
            if (!e.target.checked) {
                this.cancelDelete();
            }
        },
        updateFiles(filesJson) {
            if (this.prevFilesJson !== filesJson) {
                this.prevFilesJson = filesJson;
                this.files = JSON.parse(filesJson);
            }
        }
    },
    computed: {
        fileList() {
            const formatDate = timestamp => {
                const date = new Date(timestamp);
                let hours = date.getHours();
                let minutes = date.getMinutes();
                const ampm = hours >= 12 ? 'pm' : 'am';
                hours %= 12;
                hours = hours || 12;
                minutes = minutes < 10 ? `0${minutes}` : minutes;
                return `${hours}:${minutes} ${ampm}`;
            };

            const processed = [];
            const baseUrl = `${this.mediaUrl}${this.roomId}`;
            for (let i = 0; i < this.files.length; i++) {
                const currEl = this.files[i];
                if (!currEl) continue;
                const url = `${baseUrl}/${i}/${currEl.name}`;
                processed.unshift({
                    ...currEl,
                    url,
                    origPos: i,
                    folder: normalizeFolder(currEl.folder),
                    formattedDate: formatDate(currEl.ctime),
                    viewerUrl: buildViewerUrl(url),
                    thumbnailUrl: buildViewerUrl(url)
                });
            }
            return processed;
        },
        groupedFiles() {
            const groups = { aff: [], neg: [], other: [] };
            this.fileList.forEach(file => {
                groups[file.folder].push(file);
            });
            return groups;
        }
    }
};
</script>
