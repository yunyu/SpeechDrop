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
                src="/static/img/download-folder.svg"
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
    <transition-group
        id="file-grid"
        name="grid-entries"
        class="flex two three-800 five-1200 six-1800 seven-2000"
        tag="div"
    >
        <div class="card-wrapper upload-wrapper" :key="-1">
            <article class="card upload-card">
                <div id="file-dropzone">
                    <img
                        class="passthrough-pointer"
                        src="/static/img/upload-icon.svg"
                        width="80"
                        height="80"
                        alt="Upload icon"
                    />
                    <p id="upload-text" class="passthrough-pointer"></p>
                </div>
            </article>
        </div>
        <div class="card-wrapper" v-for="fileEntry in fileList" :key="fileEntry.origPos">
            <article class="card">
                <div @click.self="promptDelete(fileEntry)" class="delete-button">&#215;</div>
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
                        :href="`https://docs.google.com/gview?url=${fileEntry.url}`"
                        class="half card-button card-button-left"
                    >Preview</a>
                    <a :href="fileEntry.url" class="half card-button" download>Download</a>
                </div>
            </article>
        </div>
    </transition-group>
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

Dropzone.autoDiscover = false;

const getCsrfToken = () => Cookies.get('XSRF-TOKEN');

export default {
    name: 'RoomContainer',
    data() {
        return {
            files: window.initialFiles,
            confirmDeleteFile: null,
            roomName: window.roomName,
            roomId: window.roomId,
            mediaUrl: window.mediaUrl,
            allowedMimes: window.allowedMimes,
            prevFilesJson: null,
            _onKeydown: null
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
            dropzoneElement.getElementsByTagName('p')[0].innerHTML = text;
        };

        const resetDropzone = dropzoneElement => {
            setUploadText(dropzoneElement, 'Drag files here or click to upload');
            dropzoneElement.className = 'dz-clickable';
        };

        this.$nextTick(() => {
            const dropCard = new Dropzone('div#file-dropzone', {
                url: `/${this.roomId}/upload`,
                addedfile() {},
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
                    ga('send', 'event', 'Room', 'upload', this.roomId);
                },
                createImageThumbnails: false,
                maxFilesize: 10,
                uploadMultiple: false,
                acceptedFiles: this.allowedMimes
            });
            resetDropzone(dropCard.element);
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
                currEl.url = `${baseUrl}/${i}/${currEl.name}`;
                currEl.origPos = i;
                currEl.formattedDate = formatDate(currEl.ctime);
                processed.unshift(currEl);
            }
            return processed;
        }
    }
};
</script>
