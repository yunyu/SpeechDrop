function getCsrfToken() {
    return Cookies.get('XSRF-TOKEN');
}

new Vue({
    el: '#room-container',
    data: {
        files: initialFiles
    },
    mounted() {
        ga('send', 'event', 'Room', 'join', roomId);

        // Token refresh
        setInterval(() => {
            const r = new XMLHttpRequest();
            r.open('GET', `/${roomId}/index`, true);
            r.send();
        }, 600000);

        const eb = new EventBus('/sock');
        eb.onopen = () => {
            eb.registerHandler(`speechdrop.room.${roomId}`, (e, m) =>
                this.updateFiles(m.body)
            );
        };
        eb.enableReconnect(true);

        function setUploadText(dropzoneElement, text) {
            dropzoneElement.getElementsByTagName('p')[0].innerHTML = text;
        }

        function resetDropzone(dropzoneElement) {
            setUploadText(
                dropzoneElement,
                'Drag files here or click to upload'
            );
            dropzoneElement.className = 'dz-clickable';
        }

        this.$nextTick(() => {
            const dropCard = new Dropzone('div#file-dropzone', {
                url: `/${roomId}/upload`,
                addedfile(file) {},
                uploadprogress(file, progress, bytes) {
                    setUploadText(
                        dropCard.element,
                        `Uploading (${Math.floor(progress)}%)`
                    );
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
                error(file, errorMsg) {
                    resetDropzone(dropCard.element);
                    setUploadText(
                        dropCard.element,
                        'Upload failed, please retry.'
                    );
                    dropCard.element.className += ' dropzone-fail';
                    console.log(errorMsg);
                    setTimeout(() => {
                        resetDropzone(dropCard.element);
                    }, 2000);
                },
                sending(file, xhr, formData) {
                    formData.append('X-XSRF-TOKEN', getCsrfToken());
                    ga('send', 'event', 'Room', 'upload', roomId);
                },
                createImageThumbnails: false,
                maxFilesize: 5,
                uploadMultiple: false,
                acceptedFiles: allowedMimes
            });
            resetDropzone(dropCard.element);
        });
    },
    methods: {
        deleteFile(fileIndex) {
            const r = new XMLHttpRequest();
            r.open('POST', `/${roomId}/delete`, true);
            this.$set(this.files, fileIndex, null);
            const data = `fileIndex=${fileIndex}`;
            r.setRequestHeader(
                'Content-Type',
                'application/x-www-form-urlencoded'
            );
            r.setRequestHeader('X-XSRF-TOKEN', getCsrfToken());
            r.onload = () => this.updateFiles(r.response);
            r.send(data);
            ga('send', 'event', 'Room', 'delete', roomId);
        },
        // Nasty workaround for https://github.com/vuejs/vue/issues/5800
        updateFiles(filesJson) {
            if (this.prevFilesJson !== filesJson) {
                this.prevFilesJson = filesJson;
                this.files = JSON.parse(filesJson);
            }
        }
    },
    computed: {
        fileList() {
            function formatDate(timestamp) {
                const date = new Date(timestamp);
                let hours = date.getHours();
                let minutes = date.getMinutes();
                const ampm = hours >= 12 ? 'pm' : 'am';
                hours = hours % 12;
                hours = hours ? hours : 12;
                minutes = minutes < 10 ? `0${minutes}` : minutes;
                return `${hours}:${minutes} ${ampm}`;
            }

            const processed = [];
            for (let i = 0; i < this.files.length; i++) {
                const currEl = this.files[i];
                if (!currEl) continue;
                currEl.url = `${mediaUrl + roomId}/${i}/${currEl.name}`;
                currEl.origPos = i;
                currEl.formattedDate = formatDate(currEl.ctime);
                processed.unshift(currEl);
            }
            return processed;
        }
    }
});
